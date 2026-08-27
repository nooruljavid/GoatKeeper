package com.goatkeeper.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import com.goatkeeper.app.data.FarmDatabase
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = FarmDatabase.create(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF059669),
                    secondary = Color(0xFFF59E0B)
                )
            ) {
                GoatKeeperApp(database.dao(), ::shareReport)
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
