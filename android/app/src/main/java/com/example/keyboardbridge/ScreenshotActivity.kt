package com.example.keyboardbridge

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenshotActivity : AppCompatActivity() {

    companion object {
        const val LATEST_SCREENSHOT_NAME = "latest_wifisync_screenshot.png"
        private const val SAVE_REQUEST_CODE = 3001
    }

    private lateinit var screenshotFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        screenshotFile = File(cacheDir, LATEST_SCREENSHOT_NAME)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Latest WiFiSync Screenshot"
            textSize = 22f
        }

        val imageView = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(0, 24, 0, 24)
        }

        val status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 0, 0, 20)
        }

        val shareButton = Button(this).apply {
            text = "Share"
            isEnabled = screenshotFile.exists()
            setOnClickListener { shareScreenshot() }
        }

        val saveButton = Button(this).apply {
            text = "Save a Copy"
            isEnabled = screenshotFile.exists()
            setOnClickListener { requestSaveLocation() }
        }

        if (screenshotFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(screenshotFile.absolutePath)

            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                status.text = "Received from the Windows WiFiSync client."
            } else {
                status.text = "The latest screenshot could not be decoded."
                shareButton.isEnabled = false
                saveButton.isEnabled = false
            }
        } else {
            status.text = "No screenshot has been received yet."
        }

        container.addView(title)
        container.addView(status)
        container.addView(imageView)
        container.addView(shareButton)
        container.addView(saveButton)

        val scrollView = ScrollView(this).apply {
            addView(container)
        }

        setContentView(scrollView)
    }

    private fun shareScreenshot() {
        if (!screenshotFile.exists()) return

        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            screenshotFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("WiFiSync screenshot", uri)
        }

        startActivity(
            Intent.createChooser(
                shareIntent,
                "Share screenshot"
            )
        )
    }

    private fun requestSaveLocation() {
        if (!screenshotFile.exists()) return

        val stamp = SimpleDateFormat(
            "yyyyMMdd-HHmmss",
            Locale.US
        ).format(Date())

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/png"
            putExtra(
                Intent.EXTRA_TITLE,
                "WiFiSync-$stamp.png"
            )
        }

        startActivityForResult(intent, SAVE_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Android API, retained for minSdk compatibility.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode != SAVE_REQUEST_CODE ||
            resultCode != RESULT_OK
        ) {
            return
        }

        val destination: Uri = data?.data ?: return

        try {
            FileInputStream(screenshotFile).use { input ->
                contentResolver.openOutputStream(destination)?.use { output ->
                    input.copyTo(output)
                } ?: error("Could not open the selected destination.")
            }

            Toast.makeText(
                this,
                "Screenshot saved.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (exc: Exception) {
            Toast.makeText(
                this,
                "Save failed: ${exc.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
