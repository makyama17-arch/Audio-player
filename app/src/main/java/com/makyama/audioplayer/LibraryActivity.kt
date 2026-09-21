package com.makyama.audioplayer

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
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

    private lateinit var songsContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var songCountText: TextView
    private lateinit var searchEditText: EditText
    private lateinit var menuButton: ImageButton
    private lateinit var selectedActionBar: LinearLayout
    private lateinit var selectedCountText: TextView
    private lateinit var playSelectedButton: Button

    private var audioList = mutableListOf<AudioItem>()
    private var displayedList = mutableListOf<AudioItem>()

    private val selectedIds = mutableSetOf<Long>()

    private enum class SortType {
        TITLE,
        ARTIST,
        ALBUM,
        DATE_ADDED
    }

    private var sortType = SortType.TITLE
    private var selectionMode = false

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

        songsContainer =
            findViewById(R.id.songsContainer)

        emptyText =
            findViewById(R.id.emptyText)

        songCountText =
            findViewById(R.id.songCountText)

        searchEditText =
            findViewById(R.id.searchMusicEditText)

        menuButton =
            findViewById(R.id.libraryMenuButton)

        selectedActionBar =
            findViewById(R.id.selectedActionBar)

        selectedCountText =
            findViewById(R.id.selectedCountText)

        playSelectedButton =
            findViewById(R.id.playSelectedButton)

        menuButton.setOnClickListener {
            showLibraryMenu()
        }

        playSelectedButton.setOnClickListener {
            playSelectedSongs()
        }

        setupSearch()

        checkPermission()
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
                    mediaController =
                        controllerFuture.get()
                } catch (_: Exception) {
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        super.onStop()

        mediaController?.let {
            MediaController.releaseFuture(
                controllerFuture
            )
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

            permissionLauncher.launch(
                permission
            )
        }
    }

    private fun setupSearch() {

        searchEditText.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    filterSongs(
                        s?.toString()
                            ?.trim()
                            ?: ""
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )
    }

    private fun loadAudioFiles() {

        audioList.clear()

        val collection =
            android.provider.MediaStore.Audio.Media
                .EXTERNAL_CONTENT_URI

        val projection =
            arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATE_ADDED,
                android.provider.MediaStore.Audio.Media.ALBUM_ID
            )

        val selection =
            "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"

        val sortOrder =
            "${android.provider.MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->

            val idColumn =
                cursor.getColumnIndexOrThrow(
                    android.provider.MediaStore.Audio.Media._ID
                )

            val titleColumn =
                cursor.getColumnIndexOrThrow(
                    android.provider.MediaStore.Audio.Media.TITLE
                )

            val artistColumn =
                cursor.getColumnIndexOrThrow(
                    android.provider.MediaStore.Audio.Media.ARTIST
                )

            val albumColumn =
                cursor.getColumnIndexOrThrow(
                    android.provider.MediaStore.Audio.Media.ALBUM
                )

            val durationColumn =
                cursor.getColumnIndexOrThrow(
                    android.provider.MediaStore.Audio.Media.DURATION
                )

            val dateColumn =
                cursor.getColumnIndexOrThrow(
                    android.provider.MediaStore.Audio.Media.DATE_ADDED
                )

            val albumIdColumn =
                cursor.getColumnIndexOrThrow(
                    android.provider.MediaStore.Audio.Media.ALBUM_ID
                )

            while (cursor.moveToNext()) {

                val id =
                    cursor.getLong(idColumn)

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

        filterSongs(
            searchEditText.text
                ?.toString()
                ?.trim()
                ?: ""
        )
    }

    private fun filterSongs(
        query: String
    ) {

        displayedList.clear()

        if (query.isEmpty()) {

            displayedList.addAll(
                audioList
            )

        } else {

            displayedList.addAll(
                audioList.filter {

                    it.title.contains(
                        query,
                        ignoreCase = true
                    ) ||

                    it.artist.contains(
                        query,
                        ignoreCase = true
                    ) ||

                    it.album.contains(
                        query,
                        ignoreCase = true
                    )
                }
            )
        }

        showSongs()
    }

    private fun showSongs() {

        songsContainer.removeAllViews()

        songCountText.text =
            if (displayedList.size == 1) {
                "1 song"
            } else {
                "${displayedList.size} songs"
            }

        if (displayedList.isEmpty()) {

            emptyText.visibility =
                View.VISIBLE

            emptyText.text =
                if (audioList.isEmpty()) {
                    "No music found on this device."
                } else {
                    "No songs match your search."
                }

            return
        }

        emptyText.visibility =
            View.GONE

        val inflater =
            LayoutInflater.from(this)

        for (song in displayedList) {

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

            title.text =
                song.title

            artist.text =
                song.artist

            loadAlbumThumbnail(
                thumbnail,
                song.albumId
            )

            checkBox.visibility =
                if (selectionMode) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            checkBox.isChecked =
                selectedIds.contains(song.id)

            checkBox.setOnCheckedChangeListener {
                    _, checked ->

                if (checked) {
                    selectedIds.add(song.id)
                } else {
                    selectedIds.remove(song.id)
                }

                updateSelectionUI()
            }

            row.setOnClickListener {

                if (selectionMode) {

                    checkBox.isChecked =
                        !checkBox.isChecked

                } else {

                    playSingleSong(song)
                }
            }

            songsContainer.addView(row)
        }

        updateSelectionUI()
    }

    private fun loadAlbumThumbnail(
        imageView: ImageView,
        albumId: Long
    ) {

        imageView.setImageResource(
            R.drawable.makyama_logo
        )

        if (albumId <= 0) {
            return
        }

        try {

            val albumArtUri =
                Uri.parse(
                    "content://media/external/audio/albumart/$albumId"
                )

            imageView.setImageURI(
                albumArtUri
            )

        } catch (_: Exception) {

            imageView.setImageResource(
                R.drawable.makyama_logo
            )
        }
    }

    private fun updateSelectionUI() {

        if (!selectionMode) {

            selectedActionBar.visibility =
                View.GONE

            return
        }

        selectedActionBar.visibility =
            View.VISIBLE

        selectedCountText.text =
            if (selectedIds.size == 1) {
                "1 selected"
            } else {
                "${selectedIds.size} selected"
            }
    }

    private fun showLibraryMenu() {

        val popup =
            PopupMenu(
                this,
                menuButton
            )

        popup.menu.add(
            "Sort by Title"
        )

        popup.menu.add(
            "Sort by Artist"
        )

        popup.menu.add(
            "Sort by Album"
        )

        popup.menu.add(
            "Sort by Date added"
        )

        popup.menu.add(
            "Select songs"
        )

        popup.menu.add(
            "Select all"
        )

        popup.menu.add(
            "Clear selection"
        )

        popup.menu.add(
            "Play selected"
        )

        popup.setOnMenuItemClickListener {

            when (it.title.toString()) {

                "Sort by Title" -> {
                    sortType =
                        SortType.TITLE

                    sortAudioList()
                }

                "Sort by Artist" -> {
                    sortType =
                        SortType.ARTIST

                    sortAudioList()
                }

                "Sort by Album" -> {
                    sortType =
                        SortType.ALBUM

                    sortAudioList()
                }

                "Sort by Date added" -> {
                    sortType =
                        SortType.DATE_ADDED

                    sortAudioList()
                }

                "Select songs" -> {
                    enterSelectionMode()
                }

                "Select all" -> {
                    selectAllSongs()
                }

                "Clear selection" -> {
                    clearSelection()
                }

                "Play selected" -> {
                    playSelectedSongs()
                }
            }

            true
        }

        popup.show()
    }

    private fun enterSelectionMode() {

        selectionMode = true

        showSongs()

        Toast.makeText(
            this,
            "Select the songs you want to play.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun selectAllSongs() {

        if (audioList.isEmpty()) {
            return
        }

        selectionMode = true

        selectedIds.clear()

        audioList.forEach {
            selectedIds.add(it.id)
        }

        showSongs()
    }

    private fun clearSelection() {

        selectedIds.clear()

        selectionMode = false

        showSongs()
    }

    private fun playSingleSong(
        song: AudioItem
    ) {

        val controller =
            mediaController

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
            Intent(
                this,
                PlayerActivity::class.java
            )
        )
    }

    private fun playSelectedSongs() {

        val controller =
            mediaController

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
                selectedIds.contains(
                    it.id
                )
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
            Intent(
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
            .setUri(
                Uri.parse(song.uri)
            )
            .setMediaMetadata(
                metadata
            )
            .build()
    }
}
