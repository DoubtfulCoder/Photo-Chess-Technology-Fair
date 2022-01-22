/**
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.codelabs.objectdetection

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity(), View.OnClickListener {
    companion object {
        const val TAG = "TFLite - ODT"
        const val REQUEST_IMAGE_CAPTURE: Int = 1
        private const val MAX_FONT_SIZE = 96F
    }

    private lateinit var captureImageFab: Button
    private lateinit var inputImageView: ImageView
    private lateinit var imgSampleOne: ImageView
    private lateinit var imgSampleTwo: ImageView
    private lateinit var imgSampleThree: ImageView
    private lateinit var tvPlaceholder: TextView
    private lateinit var currentPhotoPath: String

    private lateinit var webView: WebView
    private val URL = "https://doubtfulcoder.github.io/chess-fen/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        captureImageFab = findViewById(R.id.captureImageFab)
        inputImageView = findViewById(R.id.imageView)
        imgSampleOne = findViewById(R.id.imgSampleOne)
        imgSampleTwo = findViewById(R.id.imgSampleTwo)
        imgSampleThree = findViewById(R.id.imgSampleThree)
        tvPlaceholder = findViewById(R.id.tvPlaceholder)

        captureImageFab.setOnClickListener(this)
        imgSampleOne.setOnClickListener(this)
        imgSampleTwo.setOnClickListener(this)
        imgSampleThree.setOnClickListener(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE &&
            resultCode == Activity.RESULT_OK
        ) {
            setViewAndDetect(getCapturedImage())
        }
    }

    /**
     * onClick(v: View?)
     *      Detect touches on the UI components
     */
    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.captureImageFab -> {
                try {
                    dispatchTakePictureIntent()
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, e.message.toString())
                }
            }
            R.id.imgSampleOne -> {
                setViewAndDetect(getSampleImage(R.drawable.img_meal_one))
            }
            R.id.imgSampleTwo -> {
                setViewAndDetect(getSampleImage(R.drawable.img_meal_two))
            }
            R.id.imgSampleThree -> {
                setViewAndDetect(getSampleImage(R.drawable.img_meal_three))
            }
        }
    }

    /**
     * runObjectDetection(bitmap: Bitmap)
     *      TFLite Object Detection function
     */
    private fun runObjectDetection(bitmap: Bitmap) {
        // Step 1: Create TFLite's TensorImage object
        val image = TensorImage.fromBitmap(bitmap)

        // Step 2: Initialize the detector object
        val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(0.3f)
                .build()
        val detector = ObjectDetector.createFromFileAndOptions(
                this,
                "pieces.tflite",
                options
        )

        // Step 3: Feed given image to the detector
        val results = detector.detect(image)

        // Step 4: Parse the detection result and show it
        val resultToDisplay = results.map {
            // Get the top-1 category and craft the display text
            val category = it.categories.first()
            val text = "${category.label}, ${category.score.times(100).toInt()}%"

            // Create a data object to display the detection result
            DetectionResult(it.boundingBox, text)
        }
        debugPrint(results)

        val detector2 = ObjectDetector.createFromFileAndOptions(
            this,
            "board.tflite",
            options
        )
        // Step 3: Feed given image to the detector
        val results2 = detector2.detect(image)
        val resultToDisplay2 = results2.map {
            // Get the top-1 category and craft the display text
            val category = it.categories.first()
            val text = "${category.label}, ${category.score.times(100).toInt()}%"

            // Create a data object to display the detection result
            DetectionResult(it.boundingBox, text)
        }
        val finalResult = resultToDisplay + resultToDisplay2
//        finalResult
//        finalResult.addAll(resultToDisplay)
//        finalResult.addAll(resultToDisplay2)

        debugPrint(results2)

        val fenPos = convertToFen(results2, results)

        // Step 4: Parse the detection result and show it
//        val resultToDisplay2 = results.map {
//            // Get the top-1 category and craft the display text
//            val category = it.categories.first()
//            val text = "${category.label}, ${category.score.times(100).toInt()}%"
//
//            // Create a data object to display the detection result
//            DetectionResult(it.boundingBox, text)
//        }

        // Draw the detection result on the bitmap and show it.
        val imgWithResult = drawDetectionResult(bitmap, finalResult)
        runOnUiThread {
            inputImageView.setImageBitmap(imgWithResult)
        }

        // Loads up the board with the FEN
//        val handler = Handler()
//        handler.postDelayed({
//            loadPos(fenPos)
//        }, 10000) // Waits 10 seconds before loading
        loadPos(fenPos)
    }

    /**
     * debugPrint(visionObjects: List<Detection>)
     *      Print the detection result to logcat to examine
     */
    private fun debugPrint(results : List<Detection>) {
        for ((i, obj) in results.withIndex()) {
            val box = obj.boundingBox

            Log.d(TAG, "Detected object: ${i} ")
            Log.d(TAG, "  boundingBox: (${box.left}, ${box.top}) - (${box.right},${box.bottom})")

            for ((j, category) in obj.categories.withIndex()) {
                Log.d(TAG, "    Label $j: ${category.label}")
                val confidence: Int = category.score.times(100).toInt()
                Log.d(TAG, "    Confidence: ${confidence}%")
            }
        }
    }

    /**
     * convertToFen(pieces: List<Detection>)
     *      Converts piece detection to Chess FEN format
     */
    private fun convertToFen(
        board : List<Detection>, pieces : List<Detection>
    ): String  {
        val fenBoard = Array(8) {
            arrayOfNulls<String>(
                8
            )
        }
        // Variables for board
        var boardLeft: Float = 0.0F
        var boardRight: Float = 0.0F
        var boardTop: Float = 0.0F
        var boardBottom: Float = 0.0F
        var leftRightDiff: Float = 0.0F
        var bottomTopDiff: Float = 0.0F

        for ((i, obj) in board.withIndex()) {
            val box = obj.boundingBox
            boardLeft = box.left
            boardRight = box.right
            boardTop = box.top
            boardBottom = box.bottom
            leftRightDiff = boardRight - boardLeft
            bottomTopDiff = boardTop - boardBottom
        }

        for ((i, obj) in pieces.withIndex()) {
            val box = obj.boundingBox
            val pieceX = (box.left + box.right) / 2
            val pieceY = (box.top + box.bottom) / 2
            val boardI = 8-(Math.round((pieceY - boardBottom) / (bottomTopDiff / 8))-1)
            val boardJ = Math.round((pieceX - boardLeft) / (leftRightDiff / 8))-1
            Log.d(TAG, "boardI: $boardI")
            Log.d(TAG, "boardJ: $boardJ")

            for ((j, category) in obj.categories.withIndex()) {
                Log.d(TAG, "    Label $j: ${category.label}")
                fenBoard[boardI][boardJ] = getPieceFenCode(category.label)
                Log.d(TAG, "label: ${getPieceFenCode(category.label)}")
                Log.d(TAG, "boardJ: ${category.label}")
//                val confidence: Int = category.score.times(100).toInt()
//                Log.d(TAG, "    Confidence: ${confidence}%")
            }
        }

        Log.d(TAG, "${boardToString(fenBoard)}")
        return boardToString(fenBoard)
    }

    /**
     * getPieceFenCode(label: String)
     *      Gets FEN code of a piece (e.g. r for black rook)
     */
    private fun getPieceFenCode(label: String): String {
        Log.d(TAG, "label value: $label")
        var fenCode = ""
        // Gets fen code based on standard chess notation
        if (label.endsWith("King")) {
            fenCode = "k"
        }
        else if (label.endsWith("Queen")) {
            fenCode = "q"
        }
        else if (label.endsWith("Rook")) {
            fenCode = "r"
        }
        else if (label.endsWith("Bishop")) {
            fenCode = "b"
        }
        else if (label.endsWith("Knight")) {
            fenCode = "n"
        }
        else if (label.endsWith("Pawn")) {
            fenCode = "p"
        }
        // Capitalizes white pieces
        if (label.startsWith("White")) {
            fenCode = fenCode.toLowerCase()
        }
        Log.d(TAG, "fenCode: $fenCode")
        return fenCode
    }

    /**
     * boardToString(board: Array<Array<String?>>)
     *      Converts FEN board array to an FEN string
     */
    private fun boardToString(board: Array<Array<String?>>): String {
        var fenString = ""
        for (row in board) {
            var blankSquareCount = 0 // keeps track of blank squares
            for (value in row) {
                if (value == null) { // blank square
                    blankSquareCount++
                }
                else { // actual piece
                    if (blankSquareCount != 0) {
                        fenString += blankSquareCount
                        blankSquareCount = 0
                    }
                    fenString += value
                }
            }
            // Adds trailing blanksquares
            if (blankSquareCount != 0) { fenString += blankSquareCount }
            fenString += "/" // Row seperator
        }
        // Removes trailing slash
        fenString = fenString.substring(0, fenString.length-1)
        return fenString
    }

    /**
     * loadPos(fen: string)
     *      Loads up a chess board with FEN using WebVew
     */
    private fun loadPos(fen: String) {
        runOnUiThread {
            webView = findViewById(R.id.web);
            webView.visibility = View.VISIBLE
            //        webView.webViewClient = WebViewClient()

            webView.apply {
                loadUrl("$URL?$fen")
                settings.javaScriptEnabled = true
                //            settings.safeBrowsingEnabled = true
            }
        }
    }

    /**
     * setViewAndDetect(bitmap: Bitmap)
     *      Set image to view and call object detection
     */
    private fun setViewAndDetect(bitmap: Bitmap) {
        // Display capture image
        inputImageView.setImageBitmap(bitmap)
        tvPlaceholder.visibility = View.INVISIBLE

        // Run ODT and display result
        // Note that we run this in the background thread to avoid blocking the app UI because
        // TFLite object detection is a synchronised process.
        lifecycleScope.launch(Dispatchers.Default) { runObjectDetection(bitmap) }
    }

    /**
     * getCapturedImage():
     *      Decodes and crops the captured image from camera.
     */
    private fun getCapturedImage(): Bitmap {
        // Get the dimensions of the View
        val targetW: Int = inputImageView.width
        val targetH: Int = inputImageView.height

        val bmOptions = BitmapFactory.Options().apply {
            // Get the dimensions of the bitmap
            inJustDecodeBounds = true

            BitmapFactory.decodeFile(currentPhotoPath, this)

            val photoW: Int = outWidth
            val photoH: Int = outHeight

            // Determine how much to scale down the image
            val scaleFactor: Int = max(1, min(photoW / targetW, photoH / targetH))

            // Decode the image file into a Bitmap sized to fill the View
            inJustDecodeBounds = false
            inSampleSize = scaleFactor
            inMutable = true
        }
        val exifInterface = ExifInterface(currentPhotoPath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                rotateImage(bitmap, 90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                rotateImage(bitmap, 180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                rotateImage(bitmap, 270f)
            }
            else -> {
                bitmap
            }
        }
    }

    /**
     * getSampleImage():
     *      Get image form drawable and convert to bitmap.
     */
    private fun getSampleImage(drawable: Int): Bitmap {
        return BitmapFactory.decodeResource(resources, drawable, BitmapFactory.Options().apply {
            inMutable = true
        })
    }

    /**
     * rotateImage():
     *     Decodes and crops the captured image from camera.
     */
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    /**
     * createImageFile():
     *     Generates a temporary image file for the Camera app to write to.
     */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    /**
     * dispatchTakePictureIntent():
     *     Start the Camera app to take a photo.
     */
    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    Log.e(TAG, e.message.toString())
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "org.tensorflow.codelabs.objectdetection.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    /**
     * drawDetectionResult(bitmap: Bitmap, detectionResults: List<DetectionResult>
     *      Draw a box around each objects and show the object's name.
     */
    private fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<DetectionResult>
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectionResults.forEach {
            // draw bounding box
            pen.color = Color.RED
            pen.strokeWidth = 8F
            pen.style = Paint.Style.STROKE
            val box = it.boundingBox
            canvas.drawRect(box, pen)


            val tagSize = Rect(0, 0, 0, 0)

            // calculate the right font size
            pen.style = Paint.Style.FILL_AND_STROKE
            pen.color = Color.YELLOW
            pen.strokeWidth = 2F

            pen.textSize = MAX_FONT_SIZE
            pen.getTextBounds(it.text, 0, it.text.length, tagSize)
            val fontSize: Float = pen.textSize * box.width() / tagSize.width()

            // adjust the font size so texts are inside the bounding box
            if (fontSize < pen.textSize) pen.textSize = fontSize

            var margin = (box.width() - tagSize.width()) / 2.0F
            if (margin < 0F) margin = 0F
            canvas.drawText(
                it.text, box.left + margin,
                box.top + tagSize.height().times(1F), pen
            )
        }
        return outputBitmap
    }
}

/**
 * DetectionResult
 *      A class to store the visualization info of a detected object.
 */
data class DetectionResult(val boundingBox: RectF, val text: String)
