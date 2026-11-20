package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.service.VoxLectEngine

class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
        if (!selectedText.isNullOrEmpty()) {
            val engine = VoxLectEngine.getInstance(applicationContext)
            if (engine.hasOverlayPermission() && !engine.isOverlayRunning.value) {
                engine.startOverlayService()
            }
            engine.processAndSpeak(
                text = selectedText,
                sourceLabel = "Menu Sélection"
            )
            Toast.makeText(this, "VoxLect : lecture en cours…", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
