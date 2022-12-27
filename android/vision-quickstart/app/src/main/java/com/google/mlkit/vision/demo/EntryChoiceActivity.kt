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

package com.google.mlkit.vision.demo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.demo.preference.PreferenceUtils
import com.google.mlkit.vision.demo.preference.SettingsActivity

class EntryChoiceActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_vision_entry_choice)
    if (!allRuntimePermissionsGranted()) {
      getRuntimePermissions()
    }
    findViewById<Button>(R.id.startCamera).setOnClickListener {
      val intent =
        Intent(
          this@EntryChoiceActivity,
          com.google.mlkit.vision.demo.kotlin.ChooserActivity::class.java
        )
      startActivity(intent)
    }
    val settingsButton = findViewById<ImageButton>(R.id.openSettings)
    settingsButton.setOnClickListener {
      val intent = Intent(applicationContext, SettingsActivity::class.java)
      intent.putExtra(SettingsActivity.EXTRA_LAUNCH_SOURCE, SettingsActivity.LaunchSource.CAMERAX_LIVE_PREVIEW)
      startActivity(intent)
    }
    updateShownDistance()
    val buttonShowPopup = findViewById<Button>(R.id.button)
    buttonShowPopup.setOnClickListener {
      showPopup()
    }
  }

  private fun showPopup() {
    val builder = AlertDialog.Builder(this)
    builder.setTitle("Enter your name")
    // Set up the input
    val input = EditText(this)
    builder.setView(input)

    // Set up the buttons
    builder.setPositiveButton("OK") { dialog, which ->
      val inputText = input.text.toString()
      Toast.makeText(this, "Your name is: $inputText", Toast.LENGTH_SHORT).show()
    }
    builder.setNegativeButton("Cancel") { dialog, which ->
      dialog.cancel()
    }

    builder.show()
  }
  private fun convertFeetToMeters(feet: Float): Float {
    return (feet / 3.28084).toFloat()
  }

  override fun onResume() {
    super.onResume()
    updateShownDistance()
  }

  private fun updateShownDistance(){
    val textView = findViewById<TextView>(R.id.safeDistance)
    val safeDistance = PreferenceUtils.safeDistance(this)
    val safeDistanceUOM = PreferenceUtils.safeDistanceUOM(this)
    val safeDistanceFloat = if (safeDistanceUOM == "Feat"){
      convertFeetToMeters(safeDistance.toFloat())
    }else{
      safeDistance.toFloat();
    }
    textView.setText("${safeDistanceFloat.format(1)} $safeDistanceUOM").toString()
  }
  private fun allRuntimePermissionsGranted(): Boolean {
    for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
      permission?.let {
        if (!isPermissionGranted(this, it)) {
          return false
        }
      }
    }
    return true
  }
  private fun Float.format(digits: Int) = "%.${digits}f".format(this)
  private fun getRuntimePermissions() {
    val permissionsToRequest = ArrayList<String>()
    for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
      permission?.let {
        if (!isPermissionGranted(this, it)) {
          permissionsToRequest.add(permission)
        }
      }
    }

    if (permissionsToRequest.isNotEmpty()) {
      ActivityCompat.requestPermissions(
        this,
        permissionsToRequest.toTypedArray(),
        PERMISSION_REQUESTS
      )
    }
  }

  private fun isPermissionGranted(context: Context, permission: String): Boolean {
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    ) {
      Log.i(TAG, "Permission granted: $permission")
      return true
    }
    Log.i(TAG, "Permission NOT granted: $permission")
    return false
  }

  companion object {
    private const val TAG = "EntryChoiceActivity"
    private const val PERMISSION_REQUESTS = 1

    private val REQUIRED_RUNTIME_PERMISSIONS =
      arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
      )
  }
}
