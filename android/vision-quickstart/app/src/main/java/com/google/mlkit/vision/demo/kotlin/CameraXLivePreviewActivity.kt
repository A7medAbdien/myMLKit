/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.kotlin

import android.content.Intent
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.demo.CameraXViewModel
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.R
import com.google.mlkit.vision.demo.VisionImageProcessor
import com.google.mlkit.vision.demo.kotlin.objectdetector.ObjectDetectorProcessor
import com.google.mlkit.vision.demo.kotlin.posedetector.PoseDetectorProcessor
import com.google.mlkit.vision.demo.kotlin.segmenter.SegmenterProcessor
import com.google.mlkit.vision.demo.preference.PreferenceUtils
import com.google.mlkit.vision.demo.preference.SettingsActivity
import com.google.mlkit.vision.demo.preference.SettingsActivity.LaunchSource
import java.util.ArrayList

/** Live preview demo app for ML Kit APIs using CameraX. */
@KeepName
@RequiresApi(VERSION_CODES.LOLLIPOP)
class CameraXLivePreviewActivity :
  AppCompatActivity(), OnItemSelectedListener, CompoundButton.OnCheckedChangeListener {
  // Custom View that displays the camera feed for CameraX's Preview use case.
  // This class manages the Surface lifecycle, as well as the preview aspect ratio and orientation.
  // ! Internally, it uses either a TextureView or SurfaceView to display the camera feed.
  private var previewView: PreviewView? = null
  private var graphicOverlay: GraphicOverlay? = null

  // used to bind the lifecycle of cameras to any LifecycleOwner within an application's process.
  private var cameraProvider: ProcessCameraProvider? = null

  // camera preview stream for displaying on-screen
  private var previewUseCase: Preview? = null
  // CPU accessible images for an app to perform image analysis on.
  /**
   * ImageAnalysis acquires images from the camera via an ImageReader.
   * Each image is provided to an ImageAnalysis.Analyzer function which can be implemented by application code,
   * where it can access image data for application analysis via an ImageProxy.
   */
  // provides CPU-accessible buffers for analysis, such as for machine learning inference :)
  private var analysisUseCase: ImageAnalysis? = null

  // An interface to process the images with different vision detectors and custom image models.
  private var imageProcessor: VisionImageProcessor? = null
  private var needUpdateGraphicOverlayImageSourceInfo = false
  private var selectedModel = OBJECT_DETECTION

  // CameraSelector: A set of requirements and priorities used to select a camera or return a filtered set of cameras.
  private var lensFacing = CameraSelector.LENS_FACING_BACK
  private var cameraSelector: CameraSelector? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate")
    // insure a safe state if nothing got selected !!
    if (savedInstanceState != null) {
      selectedModel = savedInstanceState.getString(STATE_SELECTED_MODEL, OBJECT_DETECTION)
    }
    // that will select our camera
    cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

    //----------- xml connections -----------
    setContentView(R.layout.activity_vision_camerax_live_preview)
    previewView = findViewById(R.id.preview_view)
    if (previewView == null) {
      Log.d(TAG, "previewView is null")
    }
    graphicOverlay = findViewById(R.id.graphic_overlay)
    if (graphicOverlay == null) {
      Log.d(TAG, "graphicOverlay is null")
    }
    val spinner = findViewById<Spinner>(R.id.spinner)
    val options: MutableList<String> = ArrayList()
    options.add(OBJECT_DETECTION)
    options.add(OBJECT_DETECTION_CUSTOM)
    options.add(CUSTOM_AUTOML_OBJECT_DETECTION)
    options.add(POSE_DETECTION)
    options.add(SELFIE_SEGMENTATION)

    // Creating adapter for spinner
    val dataAdapter = ArrayAdapter(this, R.layout.spinner_style, options)
    // Drop down layout style - list view with radio button
    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    // attaching data adapter to spinner
    spinner.adapter = dataAdapter
    spinner.onItemSelectedListener = this
    // cam switching
    val facingSwitch = findViewById<ToggleButton>(R.id.facing_switch)
    facingSwitch.setOnCheckedChangeListener(this)

    val settingsButton = findViewById<ImageView>(R.id.settings_button)
    settingsButton.setOnClickListener {
      val intent = Intent(applicationContext, SettingsActivity::class.java)
      intent.putExtra(SettingsActivity.EXTRA_LAUNCH_SOURCE, LaunchSource.CAMERAX_LIVE_PREVIEW)
      startActivity(intent)
    }
    //--------------------------------- xml connections done ---------------------------------------

    /**
     * ViewModel
     * is a class that is responsible for preparing and managing the data for an Activity or a Fragment.
     * It also handles the communication of the Activity / Fragment with the rest of the application
     * (e.g. calling the business logic classes).
     *
     * ViewModelProvider
     * A utility class that provides ViewModels for a scope.
     *
     * ViewModelProvider.Factory which may create AndroidViewModel and ViewModel, which have an empty constructor.
     *
     * getInstance:
     * Retrieve a singleton instance of AndroidViewModelFactory.
     *
     * @param application an application to pass in {@link AndroidViewModel}
     * @return A valid {@link AndroidViewModelFactory}
     *
     * get:
     * Returns an existing ViewModel or creates a new one in the scope (usually, a fragment or an activity),
     * associated with this ViewModelProvider.
     *
     * CameraXViewModel:
     * View model for interacting with CameraX.
     *
     * processCameraProvider:
     * Create an instance which interacts with the camera service via the given application context.
     *
     * LiveData:
     * LiveData is a data holder class that can be observed within a given lifecycle.
     *
     * observe:
     * Adds the given observer to the observers list within the lifespan of the given owner.
     * @param owner    The LifecycleOwner which controls the observer
     * @param observer The observer that will receive the events
     *
     * ProcessCameraProvider:
     * A singleton which can be used to bind the lifecycle of cameras to any LifecycleOwner within an application's process.
     *
     * The Singleton Pattern is a software design pattern that restricts the instantiation of a class to just “one” instance.
     *
     */
    // To Bind CameraX useCases
    // Creating a ViewModel that can interact with CameraX with live data because it will be observed by ProcessCameraProvider!!
    // ! Sets the cameraProvider and bind UseCase of CameraX, show use of CameraX tutorial
    ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))
      .get(CameraXViewModel::class.java)
      .processCameraProvider
      .observe(
        this,
        Observer { provider: ProcessCameraProvider? ->
          cameraProvider = provider
          bindAllCameraUseCases()
        }
      )


  }

  // ---------------------- handel Adaptor -----------------------------------------
  // when the selected model change
  override fun onSaveInstanceState(bundle: Bundle) {
    super.onSaveInstanceState(bundle)
    bundle.putString(STATE_SELECTED_MODEL, selectedModel)
  }

  @Synchronized
  override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
    // An item was selected. You can retrieve the selected item using
    // parent.getItemAtPosition(pos)
    selectedModel = parent?.getItemAtPosition(pos).toString()
    Log.d(TAG, "Selected model: $selectedModel")
    // * he rebind the AnalysisUseCase, cus the adaptor will effect only the type of analysis
    bindAnalysisUseCase()
  }

  override fun onNothingSelected(parent: AdapterView<*>?) {
    // Do nothing.
  }
  // ---------------------- handel Adaptor -----------------------------------------

  // when cam changes
  // * in case of changing the cam/Lens, rebind all cases
  override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
    if (cameraProvider == null) {
      return
    }
    val newLensFacing =
      if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
        CameraSelector.LENS_FACING_BACK
      } else {
        CameraSelector.LENS_FACING_FRONT
      }
    val newCameraSelector = CameraSelector.Builder().requireLensFacing(newLensFacing).build()
    try {
      if (cameraProvider!!.hasCamera(newCameraSelector)) {
        Log.d(TAG, "Set facing to " + newLensFacing)
        lensFacing = newLensFacing
        cameraSelector = newCameraSelector
        bindAllCameraUseCases()
        return
      }
    } catch (e: CameraInfoUnavailableException) {
      // Falls through
    }
    Toast.makeText(
      applicationContext,
      "This device does not have lens with facing: $newLensFacing",
      Toast.LENGTH_SHORT
    )
      .show()
  }

  public override fun onResume() {
    super.onResume()
    bindAllCameraUseCases()
  }

  override fun onPause() {
    super.onPause()

    imageProcessor?.run { this.stop() }
  }

  public override fun onDestroy() {
    super.onDestroy()
    imageProcessor?.run { this.stop() }
  }

  private fun bindAllCameraUseCases() {
    if (cameraProvider != null) {
      // As required by CameraX API, unbinds all use cases before trying to re-bind any of them.
      cameraProvider!!.unbindAll()
      bindPreviewUseCase()
      bindAnalysisUseCase()
    }
  }

  // here the preview is known and previewUseCase is known and they will be bind with cameraProvider
  // to receive the camera data
  // * preview Use case
  private fun bindPreviewUseCase() {
    if (!PreferenceUtils.isCameraLiveViewportEnabled(this)) {
      return
    }
    if (cameraProvider == null) {
      return
    }
    if (previewUseCase != null) {
      cameraProvider!!.unbind(previewUseCase)
    }

    val builder = Preview.Builder()
    //  handel settings
    val targetResolution = PreferenceUtils.getCameraXTargetResolution(this, lensFacing)
    if (targetResolution != null) {
      builder.setTargetResolution(targetResolution)
    }
    previewUseCase = builder.build()
    // This interface is implemented by the application to provide a Surface.
    // This will be called by CameraX when it needs a Surface for Preview.
    // It also signals when the Surface is no longer in use by CameraX.
    previewUseCase!!.setSurfaceProvider(previewView!!.getSurfaceProvider())
    /**
     * @param lifecycleOwner The lifecycleOwner which controls the lifecycle transitions of the use
     *                       cases.
     * @param cameraSelector The camera selector which determines the camera to use for set of
     *                       use cases.
     * @param useCases       The use cases to bind to a lifecycle.
     *
     * @return The {@link Camera} instance which is determined by the camera selector and
     * internal requirements.
     *
     * @throws IllegalStateException    If the use case has already been bound to another lifecycle
     *                                  or method is not called on main thread.
     * @throws IllegalArgumentException If the provided camera selector is unable to resolve a
     *                                  camera to be used for the given use cases.
     */
    cameraProvider!!.bindToLifecycle(/* lifecycleOwner= */ this,
      cameraSelector!!,
      previewUseCase)
  }

  // the cameraProvider is known, previewUseCase and previewProvider are UnKnown
  // * Analysis use case
  private fun bindAnalysisUseCase() {
    if (cameraProvider == null) {
      return
    }
    if (analysisUseCase != null) {
      cameraProvider!!.unbind(analysisUseCase)
    }
    if (imageProcessor != null) {
      imageProcessor!!.stop()
    }
    /**
     *  ! all object detectors, first three cases, pass is Processor Builder and its Options to
     *  @return ObjectDetectorProcessor
     *
     *  ? Notice that PreferenceUtils is used to get the Options of the the processor
     *  PreferenceUtils: Utility class to retrieve shared preferences
     *
     *  * so our imageProcessor will be instance of ObjectDetectorProcessor or PoseDetectorProcessor
     *  */
    imageProcessor =
      try {
        when (selectedModel) {
          OBJECT_DETECTION -> {
            Log.i(TAG, "Using Object Detector Processor")
            val objectDetectorOptions =
              PreferenceUtils.getObjectDetectorOptionsForLivePreview(this)
            /* ! this is what will be assign to imageProcessor */
            ObjectDetectorProcessor(this, objectDetectorOptions)
          }
          OBJECT_DETECTION_CUSTOM -> {
            Log.i(TAG, "Using Custom Object Detector (with object labeler) Processor")
            val localModel =
              LocalModel.Builder()
                .setAssetFilePath("custom_models/object_labeler.tflite").build()
            val customObjectDetectorOptions =
              PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(this,
                localModel)
            ObjectDetectorProcessor(this, customObjectDetectorOptions)
          }
          CUSTOM_AUTOML_OBJECT_DETECTION -> {
            Log.i(TAG, "Using Custom AutoML Object Detector Processor")
            val customAutoMLODTLocalModel =
              LocalModel.Builder().setAssetManifestFilePath("automl/manifest.json")
                .build()
            val customAutoMLODTOptions =
              PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(
                this,
                customAutoMLODTLocalModel
              )
            ObjectDetectorProcessor(this, customAutoMLODTOptions)
          }
          // ! here wt we need
          POSE_DETECTION -> {
            val poseDetectorOptions =
              PreferenceUtils.getPoseDetectorOptionsForLivePreview(this)
            val shouldShowInFrameLikelihood =
              PreferenceUtils.shouldShowPoseDetectionInFrameLikelihoodLivePreview(this)
            val visualizeZ = PreferenceUtils.shouldPoseDetectionVisualizeZ(this)
            val rescaleZ =
              PreferenceUtils.shouldPoseDetectionRescaleZForVisualization(this)
            val runClassification =
              PreferenceUtils.shouldPoseDetectionRunClassification(this)
            PoseDetectorProcessor(
              this,
              poseDetectorOptions,
              shouldShowInFrameLikelihood,
              visualizeZ,
              rescaleZ,
              runClassification,
              /* isStreamMode = */ true
            )
          }
          SELFIE_SEGMENTATION -> SegmenterProcessor(this)
          else -> throw IllegalStateException("Invalid model name")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Can not create image processor: $selectedModel", e)
        Toast.makeText(
          applicationContext,
          "Can not create image processor: " + e.localizedMessage,
          Toast.LENGTH_LONG
        ).show()
        return
      }

    // ! Use Case Builder
    val builder = ImageAnalysis.Builder()
    val targetResolution = PreferenceUtils.getCameraXTargetResolution(this, lensFacing)
    if (targetResolution != null) {
      builder.setTargetResolution(targetResolution)
    }
    analysisUseCase = builder.build()

    needUpdateGraphicOverlayImageSourceInfo = true

    /**
     * @param Executor
     * @param Analyzer of the image */
    analysisUseCase?.setAnalyzer(
      // imageProcessor.processImageProxy will use another thread to run the detection underneath,
      // thus we can just runs the analyzer itself on main thread.
      /**
       * *  ContextCompat: Helper for accessing features in Context.
       * * getMainExecutor: Return an Executor that will run enqueued tasks on the main thread associated with this context.
       * This is the thread used to dispatch calls to application components (activities, services, etc).
       * */
      ContextCompat.getMainExecutor(this),
      ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
        /* ------------------- * Rotation Handling ------------------------------------- */
        if (needUpdateGraphicOverlayImageSourceInfo) {
          val isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT
          val rotationDegrees = imageProxy.imageInfo.rotationDegrees
          if (rotationDegrees == 0 || rotationDegrees == 180) {
            graphicOverlay!!.setImageSourceInfo(imageProxy.width, imageProxy.height, isImageFlipped)
          } else {
            graphicOverlay!!.setImageSourceInfo(imageProxy.height, imageProxy.width, isImageFlipped)
          }
          needUpdateGraphicOverlayImageSourceInfo = false
        }
        /* ------------------- * Rotation Handling Done ------------------------------- */

        try {
          // ! here what is important
          imageProcessor!!.processImageProxy(imageProxy, graphicOverlay)

        } catch (e: MlKitException) {
          Log.e(TAG, "Failed to process image. Error: " + e.localizedMessage)
          Toast.makeText(applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
      }
    )
    cameraProvider!!.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector!!, analysisUseCase)
  }

  companion object {
    private const val TAG = "CameraXLivePreview"
    private const val OBJECT_DETECTION = "Object Detection"
    private const val OBJECT_DETECTION_CUSTOM = "Custom Object Detection"
    private const val CUSTOM_AUTOML_OBJECT_DETECTION = "Custom AutoML Object Detection (Flower)"
    private const val POSE_DETECTION = "Pose Detection"
    private const val SELFIE_SEGMENTATION = "Selfie Segmentation"

    private const val STATE_SELECTED_MODEL = "selected_model"
  }
}
