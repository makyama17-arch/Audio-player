package com.makyama.audioplayer

data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val dateAdded: Long,
    val albumId: Long,
    val uri: String
)
