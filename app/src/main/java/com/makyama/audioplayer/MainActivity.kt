package com.makyama.audioplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null

    private lateinit var songTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var seekBar: SeekBar

    private lateinit var playButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var nextButton: ImageButton

    private val audioList = ArrayList<AudioItem>()

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted = permissions.values.any { it }

            if (granted) {
                loadAudioFiles()
            } else {
                Toast.makeText(
                    this,
                    "Ruhusu access ya audio ili app ionyeshe nyimbo.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        songTitle = findViewById(R.id.songTitle)
        artistName = findViewById(R.id.artistName)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        seekBar = findViewById(R.id.seekBar)

        playButton = findViewById(R.id.playButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)

        playButton.setOnClickListener {
            controller?.let {
                if (it.isPlaying) {
                    it.pause()
                } else {
                    it.play()
                }

                updatePlayButton()
            }
        }

        previousButton.setOnClickListener {
            controller?.seekToPreviousMediaItem()
        }

        nextButton.setOnClickListener {
            controller?.seekToNextMediaItem()
        }

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        currentTime.text = formatTime(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    controller?.seekTo(seekBar?.progress?.toLong() ?: 0L)
                }
            }
        )

        checkPermissions()
    }

    override fun onStart() {
        super.onStart()

        val sessionToken = SessionToken(
            this,
            android.content.ComponentName(
                this,
                AudioPlayerService::class.java
            )
        )

        controllerFuture = MediaController.Builder(
            this,
            sessionToken
        ).buildAsync()

        controllerFuture.addListener(
            {
                controller = controllerFuture.get()

                controller?.addListener(
                    object : androidx.media3.common.Player.Listener {

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            updatePlayButton()
                        }

                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            updateCurrentSong()
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            updateProgress()
                        }
                    }
                )

                if (audioList.isNotEmpty()) {
                    preparePlaylist()
                }

                updateCurrentSong()
                updatePlayButton()

            },
            ContextCompat.getMainExecutor(this)
        )
    }

    override fun onStop() {
        controller?.release()
        controller = null

        super.onStop()
    }

    private fun checkPermissions() {

        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }

        } else {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isEmpty()) {
            loadAudioFiles()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun loadAudioFiles() {

        audioList.clear()

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        val selection =
            "${MediaStore.Audio.Media.DURATION} > 0"

        val sortOrder =
            "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->

            val idColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )

            val titleColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )

            val artistColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST
                )

            val albumColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ALBUM
                )

            val durationColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DURATION
                )

            while (cursor.moveToNext()) {

                val id = cursor.getLong(idColumn)

                val title =
                    cursor.getString(titleColumn)
                        ?: "Unknown Song"

                val artist =
                    cursor.getString(artistColumn)
                        ?: "Unknown Artist"

                val album =
                    cursor.getString(albumColumn)
                        ?: "Unknown Album"

                val duration =
                    cursor.getLong(durationColumn)

                val uri =
                    android.content.ContentUris.withAppendedId(
                        collection,
                        id
                    )

                audioList.add(
                    AudioItem(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = uri.toString()
                    )
                )
            }
        }

        preparePlaylist()

        updateCurrentSong()
    }

    private fun preparePlaylist() {

        val mediaController = controller ?: return

        if (audioList.isEmpty()) {

            songTitle.text = "Hakuna audio"
            artistName.text = "Weka MP3/M4A/WAV kwenye simu"

            return
        }

        val mediaItems = audioList.map { audio ->

            MediaItem.Builder()
                .setUri(audio.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(audio.title)
                        .setArtist(audio.artist)
                        .setAlbumTitle(audio.album)
                        .build()
                )
                .build()
        }

        mediaController.setMediaItems(mediaItems)
        mediaController.prepare()

        updateCurrentSong()
    }

    private fun updateCurrentSong() {

        val mediaController = controller ?: return
        val index = mediaController.currentMediaItemIndex

        if (index >= 0 && index < audioList.size) {

            val audio = audioList[index]

            songTitle.text = audio.title
            artistName.text = audio.artist

            totalTime.text =
                formatTime(audio.duration)

            seekBar.max =
                audio.duration.toInt()
        }
    }

    private fun updateProgress() {

        val mediaController = controller ?: return

        val position =
            mediaController.currentPosition.coerceAtLeast(0L)

        val duration =
            mediaController.duration.coerceAtLeast(0L)

        seekBar.max =
            duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        seekBar.progress =
            position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        currentTime.text =
            formatTime(position)

        totalTime.text =
            formatTime(duration)
    }

    private fun updatePlayButton() {

        val mediaController = controller ?: return

        if (mediaController.isPlaying) {
            playButton.setImageResource(
                android.R.drawable.ic_media_pause
            )
        } else {
            playButton.setImageResource(
                android.R.drawable.ic_media_play
            )
        }
    }

    private fun formatTime(milliseconds: Long): String {

        val totalSeconds =
            milliseconds / 1000

        val minutes =
            totalSeconds / 60

        val seconds =
            totalSeconds % 60

        return String.format(
            Locale.US,
            "%02d:%02d",
            minutes,
            seconds
        )
    }
}
