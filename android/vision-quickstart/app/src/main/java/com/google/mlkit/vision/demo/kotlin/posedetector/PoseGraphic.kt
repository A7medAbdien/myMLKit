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

package com.google.mlkit.vision.demo.kotlin.posedetector

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.lang.Math.max
import java.lang.Math.min
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/** Draw the detected pose in preview. */
class PoseGraphic
internal constructor(
  overlay: GraphicOverlay,
  private val pose: Pose,
  private val showInFrameLikelihood: Boolean,
  private val visualizeZ: Boolean,
  private val rescaleZForVisualization: Boolean,
  private val poseClassification: List<String>,
) : Graphic(overlay) {
  private var zMin = java.lang.Float.MAX_VALUE
  private var zMax = java.lang.Float.MIN_VALUE
  private val classificationTextPaint: Paint
  private val leftPaint: Paint
  private val rightPaint: Paint
  private val whitePaint: Paint

  init {
    classificationTextPaint = Paint()
    classificationTextPaint.color = Color.WHITE
    classificationTextPaint.textSize = POSE_CLASSIFICATION_TEXT_SIZE
    classificationTextPaint.setShadowLayer(5.0f, 0f, 0f, Color.BLACK)

    whitePaint = Paint()
    whitePaint.strokeWidth = STROKE_WIDTH
    whitePaint.color = Color.WHITE
    whitePaint.textSize = IN_FRAME_LIKELIHOOD_TEXT_SIZE
    leftPaint = Paint()
    leftPaint.strokeWidth = STROKE_WIDTH
    leftPaint.color = Color.GREEN
    rightPaint = Paint()
    rightPaint.strokeWidth = STROKE_WIDTH
    rightPaint.color = Color.YELLOW
  }

  override fun draw(canvas: Canvas) {

    val landmarks = pose.allPoseLandmarks
    if (landmarks.isEmpty()) {
      return
    }

    val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
    val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
    val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
    val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
    val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)

    val landmarksSub: List<PoseLandmark?> =
      listOf(nose, rightShoulder, leftShoulder, rightHip, leftHip)


    // Draw all the points
    for (landmark in landmarksSub) {
      drawPoint(canvas, landmark!!, whitePaint)
      if (visualizeZ && rescaleZForVisualization) {
        zMin = min(zMin, landmark.position3D.z)
        zMax = max(zMax, landmark.position3D.z)
      }
      // Log.i("test", (landmark.position3D).toString());
    }

    drawLine(canvas, leftShoulder, rightShoulder, whitePaint)
    drawLine(canvas, leftHip, rightHip, whitePaint)

    // Left body
    drawLine(canvas, leftShoulder, leftHip, leftPaint)
    val leftSide = calculateDistance(leftShoulder!!, leftHip!!)

    // Right body
    drawLine(canvas, rightShoulder, rightHip, rightPaint)
    val rightSide = calculateDistance(rightShoulder!!, rightHip!!)

    val avgDistance = (rightSide - leftSide) / 2


    // ! ------------------------------------- Draw on nose -------------------------------------
    if (showInFrameLikelihood) {
      canvas.drawText(
        avgDistance.toString(),
        translateX(nose!!.position.x),
        translateY(nose.position.y),
        whitePaint
      )
      Log.d("Distance: ", avgDistance.toString())
    }
  }

  // ! ------------------------------------- private use -----------------------------------------

  private fun calculateDistance(x: Float, y: Float, z: Float): Float {
    return sqrt(x.pow(2) + y.pow(2) + z.pow(2))
  }

  private fun calculateDistance(objectPose0: PoseLandmark, objectPose1: PoseLandmark): Float {
    return calculateDistance(
      objectPose0.position3D.x - objectPose1.position3D.x,
      objectPose0.position3D.y - objectPose1.position3D.y,
      (objectPose0.position3D.z - objectPose1.position3D.z) * .5f
    )
  }

  internal fun drawPoint(canvas: Canvas, landmark: PoseLandmark, paint: Paint) {
    val point = landmark.position3D
    updatePaintColorByZValue(
      paint,
      canvas,
      visualizeZ,
      rescaleZForVisualization,
      point.z,
      zMin,
      zMax
    )
    canvas.drawCircle(translateX(point.x), translateY(point.y), DOT_RADIUS, paint)
  }

  internal fun drawLine(
    canvas: Canvas,
    startLandmark: PoseLandmark?,
    endLandmark: PoseLandmark?,
    paint: Paint,
  ) {
    val start = startLandmark!!.position3D
    val end = endLandmark!!.position3D

    // Gets average z for the current body line
    val avgZInImagePixel = (start.z + end.z) / 2
    updatePaintColorByZValue(
      paint,
      canvas,
      visualizeZ,
      rescaleZForVisualization,
      avgZInImagePixel,
      zMin,
      zMax
    )

    canvas.drawLine(
      translateX(start.x),
      translateY(start.y),
      translateX(end.x),
      translateY(end.y),
      paint
    )
  }

  companion object {

    private val DOT_RADIUS = 8.0f
    private val IN_FRAME_LIKELIHOOD_TEXT_SIZE = 30.0f
    private val STROKE_WIDTH = 10.0f
    private val POSE_CLASSIFICATION_TEXT_SIZE = 60.0f
  }
}
