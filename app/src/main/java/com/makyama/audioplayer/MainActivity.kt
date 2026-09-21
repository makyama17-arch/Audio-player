package com.makyama.audioplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var controller: MediaController? = null
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    private lateinit var songTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var seekBar: SeekBar

    private lateinit var playButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var rewindButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var shuffleButton: ImageButton

    private lateinit var speedButton: TextView
    private lateinit var sortSpinner: Spinner
    private lateinit var playlistContainer: LinearLayout

    private val audioList = ArrayList<AudioItem>()

    private var currentSort = SortType.TITLE

    private enum class SortType {
        TITLE,
        ARTIST,
        ALBUM,
        DATE_ADDED
    }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val audioGranted =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
                } else {
                    permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                }

            if (audioGranted) {
                loadAudioFiles()
            } else {
                Toast.makeText(
                    this,
                    "Ruhusu app access ya audio zako.",
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
        rewindButton = findViewById(R.id.rewindButton)
        forwardButton = findViewById(R.id.forwardButton)
        shuffleButton = findViewById(R.id.shuffleButton)

        speedButton = findViewById(R.id.speedButton)
        sortSpinner = findViewById(R.id.sortSpinner)
        playlistContainer = findViewById(R.id.playlistContainer)

        setupControls()
        setupSort()

        checkPermissions()
    }

    private fun setupControls() {

        playButton.setOnClickListener {
            controller?.let {
                if (it.isPlaying) {
                    it.pause()
                } else {
                    if (it.mediaItemCount > 0) {
                        it.play()
                    }
                }
            }
        }

        previousButton.setOnClickListener {
            controller?.seekToPreviousMediaItem()
        }

        nextButton.setOnClickListener {
            controller?.seekToNextMediaItem()
        }

        rewindButton.setOnClickListener {
            seekBy(-10_000L)
        }

        forwardButton.setOnClickListener {
            seekBy(10_000L)
        }

        shuffleButton.setOnClickListener {

            controller?.let {

                it.shuffleModeEnabled =
                    !it.shuffleModeEnabled

                updateShuffleButton()

                Toast.makeText(
                    this,
                    if (it.shuffleModeEnabled)
                        "Shuffle ON"
                    else
                        "Shuffle OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        speedButton.setOnClickListener {
            changePlaybackSpeed()
        }

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        currentTime.text =
                            formatTime(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {
                    controller?.seekTo(
                        seekBar?.progress?.toLong() ?: 0L
                    )
                }
            }
        )
    }

    private fun setupSort() {

        val options = arrayOf(
            "Title",
            "Artist",
            "Album",
            "Date added"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        sortSpinner.adapter = adapter

        sortSpinner.setSelection(0)

        sortSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {

                    currentSort =
                        when (position) {
                            0 -> SortType.TITLE
                            1 -> SortType.ARTIST
                            2 -> SortType.ALBUM
                            else -> SortType.DATE_ADDED
                        }

                    if (audioList.isNotEmpty()) {
                        sortAudioList()
                    }
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {
                }
            }
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

        controllerFuture =
            MediaController.Builder(
                this,
                sessionToken
            ).buildAsync()

        controllerFuture.addListener(
            {

                controller = controllerFuture.get()

                controller?.addListener(
                    object : Player.Listener {

                        override fun onIsPlayingChanged(
                            isPlaying: Boolean
                        ) {
                            updatePlayButton()
                        }

                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            updateCurrentSong()
                            updateProgress()
                        }

                        override fun onPlaybackStateChanged(
                            playbackState: Int
                        ) {
                            updateProgress()
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int
                        ) {
                            updateProgress()
                            updateCurrentSong()
                        }

                        override fun onPlaybackParametersChanged(
                            playbackParameters: PlaybackParameters
                        ) {
                            updateSpeedButton()
                        }

                        override fun onShuffleModeEnabledChanged(
                            shuffleModeEnabled: Boolean
                        ) {
                            updateShuffleButton()
                        }
                    }
                )

                if (audioList.isNotEmpty()) {
                    preparePlaylist()
                }

                updateCurrentSong()
                updatePlayButton()
                updateShuffleButton()
                updateSpeedButton()

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

        val permissions = ArrayList<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            }

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }

        } else {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }

        if (permissions.isEmpty()) {
            loadAudioFiles()
        } else {
            permissionLauncher.launch(
                permissions.toTypedArray()
            )
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
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
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

                val id =
                    cursor.getLong(idColumn)

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

        sortAudioList()
    }

    private fun sortAudioList() {

        when (currentSort) {

            SortType.TITLE -> {
                audioList.sortBy {
                    it.title.lowercase(Locale.getDefault())
                }
            }

            SortType.ARTIST -> {
                audioList.sortBy {
                    it.artist.lowercase(Locale.getDefault())
                }
            }

            SortType.ALBUM -> {
                audioList.sortBy {
                    it.album.lowercase(Locale.getDefault())
                }
            }

            SortType.DATE_ADDED -> {
                audioList.sortByDescending {
                    it.id
                }
            }
        }

        showPlaylist()
        preparePlaylist()
    }

    private fun showPlaylist() {

        playlistContainer.removeAllViews()

        if (audioList.isEmpty()) {

            val emptyText = TextView(this)

            emptyText.text =
                "Hakuna audio zilizopatikana kwenye simu."

            emptyText.textSize = 16f
            emptyText.setTextColor(
                android.graphics.Color.WHITE
            )

            emptyText.setPadding(
                16,
                30,
                16,
                30
            )

            playlistContainer.addView(emptyText)

            return
        }

        val inflater =
            LayoutInflater.from(this)

        audioList.forEachIndexed { index, audio ->

            val row =
                inflater.inflate(
                    R.layout.audio_row,
                    playlistContainer,
                    false
                )

            val title =
                row.findViewById<TextView>(
                    R.id.audioTitle
                )

            val artist =
                row.findViewById<TextView>(
                    R.id.audioArtist
                )

            title.text = audio.title
            artist.text = audio.artist

            row.setOnClickListener {
                playAudio(index)
            }

            playlistContainer.addView(row)
        }
    }

    private fun preparePlaylist() {

        val mediaController =
            controller ?: return

        if (audioList.isEmpty()) {
            return
        }

        val mediaItems =
            audioList.map { audio ->

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

        mediaController.setMediaItems(
            mediaItems,
            false
        )

        mediaController.prepare()

        // Muhimu:
        // Usianze audio automatically.
        mediaController.pause()

        updateCurrentSong()
    }

    private fun playAudio(index: Int) {

        val mediaController =
            controller ?: return

        if (
            index < 0 ||
            index >= audioList.size
        ) {
            return
        }

        mediaController.seekToDefaultPosition(index)
        mediaController.play()

        updateCurrentSong()
    }

    private fun seekBy(milliseconds: Long) {

        val mediaController =
            controller ?: return

        val current =
            mediaController.currentPosition

        val duration =
            mediaController.duration

        val newPosition =
            (current + milliseconds)
                .coerceAtLeast(0L)
                .let {
                    if (duration > 0) {
                        it.coerceAtMost(duration)
                    } else {
                        it
                    }
                }

        mediaController.seekTo(newPosition)

        updateProgress()
    }

    private fun changePlaybackSpeed() {

        val mediaController =
            controller ?: return

        val currentSpeed =
            mediaController.playbackParameters.speed

        val nextSpeed =
            when (currentSpeed) {
                0.5f -> 0.75f
                0.75f -> 1.0f
                1.0f -> 1.25f
                1.25f -> 1.5f
                1.5f -> 2.0f
                else -> 0.5f
            }

        mediaController.setPlaybackSpeed(
            nextSpeed
        )

        speedButton.text =
            "${nextSpeed}x"
    }

    private fun updateCurrentSong() {

        val mediaController =
            controller ?: return

        val index =
            mediaController.currentMediaItemIndex

        if (
            index >= 0 &&
            index < audioList.size
        ) {

            val audio =
                audioList[index]

            songTitle.text =
                audio.title

            artistName.text =
                audio.artist

            totalTime.text =
                formatTime(audio.duration)

            seekBar.max =
                audio.duration
                    .coerceAtMost(
                        Int.MAX_VALUE.toLong()
                    )
                    .toInt()

            highlightCurrentSong(index)
        }
    }

    private fun highlightCurrentSong(
        currentIndex: Int
    ) {

        for (
            i in 0 until playlistContainer.childCount
        ) {

            val child =
                playlistContainer.getChildAt(i)

            child.alpha =
                if (i == currentIndex) {
                    1.0f
                } else {
                    0.65f
                }
        }
    }

    private fun updateProgress() {

        val mediaController =
            controller ?: return

        val position =
            mediaController.currentPosition
                .coerceAtLeast(0L)

        val duration =
            mediaController.duration
                .coerceAtLeast(0L)

        if (duration > 0) {

            seekBar.max =
                duration
                    .coerceAtMost(
                        Int.MAX_VALUE.toLong()
                    )
                    .toInt()

            seekBar.progress =
                position
                    .coerceAtMost(
                        Int.MAX_VALUE.toLong()
                    )
                    .toInt()
        }

        currentTime.text =
            formatTime(position)

        totalTime.text =
            formatTime(duration)
    }

    private fun updatePlayButton() {

        val mediaController =
            controller ?: return

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

    private fun updateShuffleButton() {

        val mediaController =
            controller ?: return

        shuffleButton.alpha =
            if (mediaController.shuffleModeEnabled) {
                1.0f
            } else {
                0.55f
            }
    }

    private fun updateSpeedButton() {

        val mediaController =
            controller ?: return

        val speed =
            mediaController.playbackParameters.speed

        speedButton.text =
            "${speed}x"
    }

    private fun formatTime(
        milliseconds: Long
    ): String {

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
