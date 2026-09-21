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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
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

    private val handler =
        Handler(Looper.getMainLooper())

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

        setContentView(
            R.layout.activity_player
        )

        thumbnail =
            findViewById(R.id.playerThumbnail)

        titleText =
            findViewById(R.id.playerTitle)

        artistText =
            findViewById(R.id.playerArtist)

        currentTimeText =
            findViewById(R.id.currentTime)

        durationText =
            findViewById(R.id.durationTime)

        seekBar =
            findViewById(R.id.playerSeekBar)

        playButton =
            findViewById(R.id.playButton)

        shuffleButton =
            findViewById(R.id.shuffleButton)

        speedButton =
            findViewById(R.id.speedButton)

        emptyText =
            findViewById(R.id.emptyPlayerText)

        openLibraryButton =
            findViewById(R.id.openLibraryButton)

        playButton.setOnClickListener {
            controller?.let {
                if (it.isPlaying) {
                    it.pause()
                } else {
                    it.play()
                }
            }
        }

        findViewById<ImageButton>(
            R.id.previousButton
        ).setOnClickListener {
            controller?.seekToPreviousMediaItem()
        }

        findViewById<ImageButton>(
            R.id.nextButton
        ).setOnClickListener {
            controller?.seekToNextMediaItem()
        }

        findViewById<ImageButton>(
            R.id.rewindButton
        ).setOnClickListener {
            controller?.let {
                it.seekTo(
                    (it.currentPosition - 10_000L)
                        .coerceAtLeast(0L)
                )
            }
        }

        findViewById<ImageButton>(
            R.id.forwardButton
        ).setOnClickListener {
            controller?.let {
                val newPosition =
                    (it.currentPosition + 10_000L)
                        .coerceAtMost(it.duration)

                it.seekTo(newPosition)
            }
        }

        shuffleButton.setOnClickListener {
            controller?.let {
                it.shuffleModeEnabled =
                    !it.shuffleModeEnabled

                updateShuffleButton()
            }
        }

        speedButton.setOnClickListener {
            changeSpeed()
        }

        openLibraryButton.setOnClickListener {
            startActivity(
                android.content.Intent(
                    this,
                    LibraryActivity::class.java
                )
            )
        }

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        controller?.let {
                            if (it.duration > 0) {
                                val position =
                                    it.duration *
                                            progress / 100L

                                it.seekTo(position)
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

                    controller =
                        controllerFuture.get()

                    controller?.addListener(
                        playerListener
                    )

                    /*
                     * IMPORTANT:
                     * We only connect to the existing player.
                     * We DO NOT reset the playlist here.
                     *
                     * This means locking/unlocking the phone
                     * will not restart or stop playback.
                     */

                    updatePlayerUI()

                    handler.post(
                        progressRunnable
                    )

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

        if (player == null ||
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
        updateProgress()
    }

    private fun showEmptyPlayer() {

        emptyText.visibility = View.VISIBLE
        openLibraryButton.visibility = View.VISIBLE

        thumbnail.visibility = View.VISIBLE
        thumbnail.setImageResource(
            R.drawable.makyama_logo
        )

        titleText.text = "No audio selected"
        artistText.text = "Open Music to choose a song"

        seekBar.progress = 0

        currentTimeText.text = "0:00"
        durationText.text = "0:00"

        playButton.setImageResource(
            android.R.drawable.ic_media_play
        )
    }

    private fun updatePlayButton() {

        val player = controller

        if (player?.isPlaying == true) {
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

        val enabled =
            controller?.shuffleModeEnabled == true

        shuffleButton.text =
            if (enabled) {
                "SHUFFLE: ON"
            } else {
                "SHUFFLE: OFF"
            }
    }

    private fun updateProgress() {

        val player = controller
            ?: return

        if (player.mediaItemCount == 0) {
            return
        }

        val duration =
            player.duration

        val position =
            player.currentPosition

        if (duration > 0) {

            seekBar.progress =
                ((position * 100L) / duration)
                    .toInt()
                    .coerceIn(0, 100)
        }

        currentTimeText.text =
            formatTime(position)

        durationTimeText(duration)
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

        if (milliseconds <= 0) {
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

    private fun changeSpeed() {

        val player = controller
            ?: return

        val currentSpeed =
            player.playbackParameters.speed

        val newSpeed =
            when {
                currentSpeed < 0.76f -> 1.0f
                currentSpeed < 1.26f -> 1.25f
                currentSpeed < 1.51f -> 1.5f
                currentSpeed < 2.01f -> 2.0f
                else -> 0.75f
            }

        player.setPlaybackSpeed(
            newSpeed
        )

        speedButton.text =
            "SPEED ${newSpeed}x"
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
                    MediaMetadataRetriever? = null

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
