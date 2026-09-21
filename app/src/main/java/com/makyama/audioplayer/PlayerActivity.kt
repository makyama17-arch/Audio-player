package com.makyama.audioplayer

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import android.media.MediaMetadataRetriever

class PlayerActivity : AppCompatActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null

    private lateinit var thumbnail: ImageView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var currentTimeText: TextView
    private lateinit var durationText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: ImageButton
    private lateinit var shuffleButton: Button
    private lateinit var speedButton: Button
    private lateinit var emptyText: TextView
    private lateinit var openLibraryButton: Button

    // Speed inayofuata kila unapobonyeza button.
    private val speedOptions = floatArrayOf(
        0.75f,
        1.0f,
        1.25f,
        1.5f,
        2.0f
    )

    private var currentSpeedIndex = 1

    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable =
        object : Runnable {
            override fun run() {
                updateProgress()
                handler.postDelayed(this, 500)
            }
        }

    private val playerListener =
        object : Player.Listener {

            override fun onEvents(
                player: Player,
                events: Player.Events
            ) {
                updatePlayerUI()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_player)

        thumbnail = findViewById(R.id.playerThumbnail)
        titleText = findViewById(R.id.playerTitle)
        artistText = findViewById(R.id.playerArtist)
        currentTimeText = findViewById(R.id.currentTime)
        durationText = findViewById(R.id.durationTime)
        seekBar = findViewById(R.id.playerSeekBar)
        playButton = findViewById(R.id.playButton)
        shuffleButton = findViewById(R.id.shuffleButton)
        speedButton = findViewById(R.id.speedButton)
        emptyText = findViewById(R.id.emptyPlayerText)
        openLibraryButton = findViewById(R.id.openLibraryButton)

        // PLAY / PAUSE
        playButton.setOnClickListener {
            controller?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
        }

        // PREVIOUS
        findViewById<ImageButton>(R.id.previousButton).setOnClickListener {
            controller?.let { player ->
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                }
            }
        }

        // NEXT
        findViewById<ImageButton>(R.id.nextButton).setOnClickListener {
            controller?.let { player ->
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                }
            }
        }

        // REWIND 10 SECONDS
        findViewById<ImageButton>(R.id.rewindButton).setOnClickListener {
            controller?.let { player ->
                val newPosition =
                    (player.currentPosition - 10_000L)
                        .coerceAtLeast(0L)

                player.seekTo(newPosition)
            }
        }

        // FORWARD 10 SECONDS
        findViewById<ImageButton>(R.id.forwardButton).setOnClickListener {
            controller?.let { player ->
                val duration = player.duration

                val newPosition =
                    if (duration > 0) {
                        (player.currentPosition + 10_000L)
                            .coerceAtMost(duration)
                    } else {
                        player.currentPosition + 10_000L
                    }

                player.seekTo(newPosition)
            }
        }

        // SHUFFLE
        shuffleButton.setOnClickListener {
            controller?.let { player ->
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                updateShuffleButton()
            }
        }

        // SPEED
        speedButton.setOnClickListener {
            changeSpeed()
        }

        // OPEN MUSIC
        openLibraryButton.setOnClickListener {
            startActivity(
                android.content.Intent(
                    this,
                    LibraryActivity::class.java
                )
            )
        }

        // SEEK BAR
        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        controller?.let { player ->

                            val duration = player.duration

                            if (duration > 0) {
                                val position =
                                    duration * progress / 100L

                                player.seekTo(position)
                            }
                        }
                    }
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }
            }
        )

        updateSpeedButton()
    }

    override fun onStart() {
        super.onStart()

        val sessionToken =
            SessionToken(
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
                try {
                    controller = controllerFuture.get()

                    controller?.addListener(
                        playerListener
                    )

                    // Soma speed yetu na player.
                    controller?.setPlaybackSpeed(
                        speedOptions[currentSpeedIndex]
                    )

                    updatePlayerUI()

                    handler.post(progressRunnable)

                } catch (_: Exception) {
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        super.onStop()

        handler.removeCallbacks(
            progressRunnable
        )

        controller?.removeListener(
            playerListener
        )

        controller?.let {
            MediaController.releaseFuture(
                controllerFuture
            )
        }

        controller = null
    }

    private fun updatePlayerUI() {

        val player = controller

        if (
            player == null ||
            player.mediaItemCount == 0
        ) {
            showEmptyPlayer()
            return
        }

        emptyText.visibility = View.GONE
        openLibraryButton.visibility = View.GONE

        thumbnail.visibility = View.VISIBLE
        titleText.visibility = View.VISIBLE
        artistText.visibility = View.VISIBLE

        val mediaItem =
            player.currentMediaItem

        if (mediaItem != null) {

            val metadata =
                mediaItem.mediaMetadata

            titleText.text =
                metadata.title ?: "Unknown title"

            artistText.text =
                metadata.artist ?: "Unknown artist"

            loadEmbeddedArtwork(
                mediaItem.localConfiguration?.uri
            )
        }

        updatePlayButton()
        updateShuffleButton()
        updateSpeedButton()
        updateProgress()
    }

    private fun showEmptyPlayer() {

        emptyText.visibility =
            View.VISIBLE

        openLibraryButton.visibility =
            View.VISIBLE

        thumbnail.visibility =
            View.VISIBLE

        thumbnail.setImageResource(
            R.drawable.makyama_logo
        )

        titleText.text =
            "No audio selected"

        artistText.text =
            "Open Music to choose a song"

        seekBar.progress = 0

        currentTimeText.text =
            "0:00"

        durationText.text =
            "0:00"

        playButton.setImageResource(
            android.R.drawable.ic_media_play
        )
    }

    private fun updatePlayButton() {

        if (controller?.isPlaying == true) {

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

        shuffleButton.text =
            if (
                controller?.shuffleModeEnabled == true
            ) {
                "SHUFFLE: ON"
            } else {
                "SHUFFLE: OFF"
            }
    }

    private fun changeSpeed() {

        val player = controller
            ?: return

        // Nenda kwenye speed inayofuata.
        currentSpeedIndex++

        if (
            currentSpeedIndex >= speedOptions.size
        ) {
            currentSpeedIndex = 0
        }

        val newSpeed =
            speedOptions[currentSpeedIndex]

        // Hapa ndiyo speed halisi ya ExoPlayer
        // inabadilishwa.
        player.setPlaybackSpeed(
            newSpeed
        )

        updateSpeedButton()
    }

    private fun updateSpeedButton() {

        val speed =
            speedOptions[currentSpeedIndex]

        speedButton.text =
            "SPEED ${speed}x"
    }

    private fun updateProgress() {

        val player =
            controller
                ?: return

        if (
            player.mediaItemCount == 0
        ) {
            return
        }

        val duration =
            player.duration

        val position =
            player.currentPosition

        if (duration > 0) {

            seekBar.progress =
                (
                    (position * 100L) /
                        duration
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        100
                    )
        }

        currentTimeText.text =
            formatTime(position)

        durationTimeText(
            duration
        )
    }

    private fun durationTimeText(
        duration: Long
    ) {

        durationText.text =
            formatTime(
                if (duration > 0) {
                    duration
                } else {
                    0L
                }
            )
    }

    private fun formatTime(
        milliseconds: Long
    ): String {

        if (
            milliseconds <= 0
        ) {
            return "0:00"
        }

        val totalSeconds =
            milliseconds / 1000

        val minutes =
            totalSeconds / 60

        val seconds =
            totalSeconds % 60

        return String.format(
            "%d:%02d",
            minutes,
            seconds
        )
    }

    private fun loadEmbeddedArtwork(
        uri: Uri?
    ) {

        if (uri == null) {

            thumbnail.setImageResource(
                R.drawable.makyama_logo
            )

            return
        }

        thumbnail.setImageResource(
            R.drawable.makyama_logo
        )

        Thread {

            var retriever:
                MediaMetadataRetriever? =
                null

            try {

                retriever =
                    MediaMetadataRetriever()

                retriever.setDataSource(
                    this,
                    uri
                )

                val picture =
                    retriever.embeddedPicture

                if (picture != null) {

                    val bitmap =
                        BitmapFactory.decodeByteArray(
                            picture,
                            0,
                            picture.size
                        )

                    runOnUiThread {

                        if (!isFinishing) {

                            thumbnail.setImageBitmap(
                                bitmap
                            )
                        }
                    }
                }

            } catch (_: Exception) {

                runOnUiThread {

                    thumbnail.setImageResource(
                        R.drawable.makyama_logo
                    )
                }

            } finally {

                try {
                    retriever?.release()
                } catch (_: Exception) {
                }
            }

        }.start()
    }
}
