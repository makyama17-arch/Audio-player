package com.makyama.audioplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Spinner
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
import com.google.common.util.concurrent.MoreExecutors

class LibraryActivity : AppCompatActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null

    private lateinit var songsContainer: android.widget.LinearLayout
    private lateinit var playSelectedButton: Button
    private lateinit var sortSpinner: Spinner
    private lateinit var emptyText: TextView

    private var audioList = mutableListOf<AudioItem>()
    private val selectedIds = mutableSetOf<Long>()

    private enum class SortType {
        TITLE,
        ARTIST,
        ALBUM,
        DATE_ADDED
    }

    private var sortType = SortType.TITLE

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                loadAudioFiles()
            } else {
                Toast.makeText(
                    this,
                    "Music permission is required to read your songs.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.library_activity)

        songsContainer = findViewById(R.id.songsContainer)
        playSelectedButton = findViewById(R.id.playSelectedButton)
        sortSpinner = findViewById(R.id.sortSpinner)
        emptyText = findViewById(R.id.emptyText)

        setupSort()

        playSelectedButton.setOnClickListener {
            playSelectedSongs()
        }

        checkPermission()
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
                try {
                    mediaController = controllerFuture.get()
                } catch (_: Exception) {
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        super.onStop()

        mediaController?.let {
            MediaController.releaseFuture(controllerFuture)
        }

        mediaController = null
    }

    private fun checkPermission() {
        val permission =
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        if (
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadAudioFiles()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun setupSort() {
        val options = arrayOf(
            "Title",
            "Artist",
            "Album",
            "Date added"
        )

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            options
        )

        sortSpinner.adapter = adapter

        sortSpinner.setSelection(0)

        sortSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {
                }

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    sortType = when (position) {
                        0 -> SortType.TITLE
                        1 -> SortType.ARTIST
                        2 -> SortType.ALBUM
                        else -> SortType.DATE_ADDED
                    }

                    sortAudioList()
                }
            }
    }

    private fun loadAudioFiles() {

        audioList.clear()

        val collection =
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val sortOrder =
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

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

            val dateColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DATE_ADDED
                )

            val albumIdColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ALBUM_ID
                )

            while (cursor.moveToNext()) {

                val id = cursor.getLong(idColumn)

                val title =
                    cursor.getString(titleColumn)
                        ?: "Unknown title"

                val artist =
                    cursor.getString(artistColumn)
                        ?: "Unknown artist"

                val album =
                    cursor.getString(albumColumn)
                        ?: "Unknown album"

                val duration =
                    cursor.getLong(durationColumn)

                val dateAdded =
                    cursor.getLong(dateColumn)

                val albumId =
                    cursor.getLong(albumIdColumn)

                val uri =
                    ContentUris.withAppendedId(
                        collection,
                        id
                    ).toString()

                audioList.add(
                    AudioItem(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        dateAdded = dateAdded,
                        albumId = albumId,
                        uri = uri
                    )
                )
            }
        }

        sortAudioList()
    }

    private fun sortAudioList() {

        when (sortType) {

            SortType.TITLE -> {
                audioList.sortBy {
                    it.title.lowercase()
                }
            }

            SortType.ARTIST -> {
                audioList.sortBy {
                    it.artist.lowercase()
                }
            }

            SortType.ALBUM -> {
                audioList.sortBy {
                    it.album.lowercase()
                }
            }

            SortType.DATE_ADDED -> {
                audioList.sortByDescending {
                    it.dateAdded
                }
            }
        }

        showSongs()
    }

    private fun showSongs() {

        songsContainer.removeAllViews()

        if (audioList.isEmpty()) {
            emptyText.visibility = TextView.VISIBLE
            playSelectedButton.isEnabled = false
            return
        }

        emptyText.visibility = TextView.GONE
        playSelectedButton.isEnabled = true

        val inflater = LayoutInflater.from(this)

        for (song in audioList) {

            val row =
                inflater.inflate(
                    R.layout.audio_row,
                    songsContainer,
                    false
                )

            val thumbnail =
                row.findViewById<ImageView>(
                    R.id.audioThumbnail
                )

            val title =
                row.findViewById<TextView>(
                    R.id.audioTitle
                )

            val artist =
                row.findViewById<TextView>(
                    R.id.audioArtist
                )

            val checkBox =
                row.findViewById<CheckBox>(
                    R.id.audioCheckBox
                )

            title.text = song.title
            artist.text = song.artist

            loadAlbumThumbnail(
                thumbnail,
                song.albumId
            )

            checkBox.isChecked =
                selectedIds.contains(song.id)

            checkBox.setOnCheckedChangeListener {
                    _, checked ->

                if (checked) {
                    selectedIds.add(song.id)
                } else {
                    selectedIds.remove(song.id)
                }

                updateSelectedButton()
            }

            row.setOnClickListener {

                if (!selectedIds.contains(song.id)) {
                    selectedIds.add(song.id)
                    checkBox.isChecked = true
                } else {
                    playSingleSong(song)
                }
            }

            songsContainer.addView(row)
        }

        updateSelectedButton()
    }

    private fun loadAlbumThumbnail(
        imageView: ImageView,
        albumId: Long
    ) {

        imageView.setImageResource(
            R.drawable.makyama_logo
        )

        if (albumId <= 0) return

        try {

            val albumArtUri = Uri.parse(
                "content://media/external/audio/albumart/$albumId"
            )

            imageView.setImageURI(albumArtUri)

        } catch (_: Exception) {

            imageView.setImageResource(
                R.drawable.makyama_logo
            )
        }
    }

    private fun updateSelectedButton() {

        val count = selectedIds.size

        playSelectedButton.text =
            if (count == 0) {
                "PLAY SELECTED"
            } else {
                "PLAY SELECTED ($count)"
            }
    }

    private fun playSingleSong(song: AudioItem) {

        val controller = mediaController

        if (controller == null) {
            Toast.makeText(
                this,
                "Player is still starting...",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val mediaItem =
            createMediaItem(song)

        controller.setMediaItems(
            listOf(mediaItem),
            0,
            0L
        )

        controller.prepare()
        controller.play()

        startActivity(
            android.content.Intent(
                this,
                PlayerActivity::class.java
            )
        )
    }

    private fun playSelectedSongs() {

        val controller = mediaController

        if (controller == null) {
            Toast.makeText(
                this,
                "Player is still starting...",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val selectedSongs =
            audioList.filter {
                selectedIds.contains(it.id)
            }

        if (selectedSongs.isEmpty()) {
            Toast.makeText(
                this,
                "Select at least one song.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val mediaItems =
            selectedSongs.map {
                createMediaItem(it)
            }

        controller.setMediaItems(
            mediaItems,
            0,
            0L
        )

        controller.prepare()
        controller.play()

        startActivity(
            android.content.Intent(
                this,
                PlayerActivity::class.java
            )
        )
    }

    private fun createMediaItem(
        song: AudioItem
    ): MediaItem {

        val metadata =
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .build()

        return MediaItem.Builder()
            .setUri(Uri.parse(song.uri))
            .setMediaMetadata(metadata)
            .build()
    }
}
