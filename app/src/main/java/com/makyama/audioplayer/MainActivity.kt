package com.makyama.audioplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val openMusicButton = findViewById<Button>(R.id.openMusicButton)

        openMusicButton.setOnClickListener {
            startActivity(
                Intent(this, LibraryActivity::class.java)
            )
        }
    }
}
