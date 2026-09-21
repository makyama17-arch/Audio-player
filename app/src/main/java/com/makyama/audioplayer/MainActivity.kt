package com.makyama.audioplayer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Open the music library immediately when the app starts.
        startActivity(
            Intent(
                this,
                LibraryActivity::class.java
            )
        )

        finish()
    }
}
