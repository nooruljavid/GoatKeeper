package com.goatkeeper.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import com.goatkeeper.app.data.FarmDatabase
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply a smooth fade transition for the whole activity life (esp. during language switch)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        
        super.onCreate(savedInstanceState)
        
        val database = try {
            FarmDatabase.create(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Database creation failed", e)
            null
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF059669),
                    secondary = Color(0xFFF59E0B)
                )
            ) {
                if (database != null) {
                    GoatKeeperApp(database.dao(), ::shareReport)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Critical Error: Database could not be initialized.\nPlease try clearing app data.")
                    }
                }
            }
        }
    }

    private fun shareReport(title: String, text: String, file: File? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            if (file != null) {
                val uri = FileProvider.getUriForFile(applicationContext, "com.goatkeeper.app.fileprovider", file)
                type = contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share report"))
    }
}
