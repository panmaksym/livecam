package com.example.cameralive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var buttonStartStream: Button
    private lateinit var buttonStopStream: Button
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Підключаємо макет
        setContentView(R.layout.activity_main)
        Log.i("MainActivity", "Application started")

        // Ініціалізуємо кнопки
        buttonStartStream = findViewById(R.id.button_start_stream)
        buttonStopStream = findViewById(R.id.button_stop_stream)

        // Встановлюємо обробники кліків
        buttonStartStream.setOnClickListener {
            startStream()
        }

        buttonStopStream.setOnClickListener {
            stopStream()
        }

        // Вимикаємо кнопку "Stop" на початку
        buttonStopStream.isEnabled = false

        // Перевіряємо дозволи
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("MainActivity", "Requesting permissions")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                1
            )
        } else {
            Log.i("MainActivity", "Permissions granted")
            // Додаткові дії, якщо потрібно
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.i("MainActivity", "Permissions granted")
                // Додаткові дії, якщо потрібно
            } else {
                Log.w("MainActivity", "Permissions denied, closing application")
                finish()
            }
        }
    }

    private fun startStream() {
        Log.i("MainActivity", "Starting stream")
        if (!isStreaming) {
            val intent = Intent(this, StreamService::class.java)
            ContextCompat.startForegroundService(this, intent)
            isStreaming = true
            buttonStartStream.isEnabled = false
            buttonStopStream.isEnabled = true
        } else {
            Log.i("MainActivity", "Stream is already running")
        }
    }

    private fun stopStream() {
        Log.i("MainActivity", "Stopping stream")
        if (isStreaming) {
            val intent = Intent(this, StreamService::class.java)
            stopService(intent)
            isStreaming = false
            buttonStartStream.isEnabled = true
            buttonStopStream.isEnabled = false
        } else {
            Log.i("MainActivity", "Stream is not running")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("MainActivity", "onDestroy called")
        if (isStreaming) {
            stopStream()
        }
    }
}
