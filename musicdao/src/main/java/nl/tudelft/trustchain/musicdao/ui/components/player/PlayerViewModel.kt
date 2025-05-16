@file:Suppress("DEPRECATION")

package nl.tudelft.trustchain.musicdao.ui.components.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import nl.tudelft.trustchain.musicdao.core.repositories.model.Song
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.cache.entities.AlbumEntity
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import nl.tudelft.trustchain.musicdao.ui.components.player.PlayerViewModel

class PlayerViewModel(context: Context, database: CacheDatabase) : ViewModel() {

    private val _playingTrack: MutableStateFlow<Song?> = MutableStateFlow(null)
    val playingTrack: StateFlow<Song?> = _playingTrack

    private val _coverFile: MutableStateFlow<File?> = MutableStateFlow(null)
    val coverFile: StateFlow<File?> = _coverFile

    private val _downloadedCount = MutableStateFlow(0)
    val downloadedCount: StateFlow<Int> = _downloadedCount

    val exoPlayer by lazy {
        ExoPlayer.Builder(context).build()
    }
    private var releaseLiveData: LiveData<List<AlbumEntity>> = MutableLiveData(null)

    init {
        viewModelScope.launch {

            database.dao.getAllLiveData().asFlow().collect { albumEntities ->
                _downloadedCount.value = albumEntities.count { it.isDownloaded }
            }




            releaseLiveData = database.dao.getAllLiveData()
            Log.d("MusicDisplay", releaseLiveData.value.toString())
            val allSongs: List<Pair<Song, File?>> = releaseLiveData
                .map { albumEntities ->
                    albumEntities.map { it.toAlbum() }
                        .flatMap { album ->
                            album.songs.orEmpty().map { song ->
                                song to album.cover
                            }
                        }
                }.asFlow().first()
            val (song, cover) = allSongs.firstOrNull() ?: return@launch
            song.let {
                playDownloadedTrack(it, cover)
            }
        }
    }

    private fun buildMediaSource(
        uri: Uri,
        context: Context
    ): MediaSource {
        @Suppress("DEPRECATION")
        val dataSourceFactory: DataSource.Factory =
            DefaultDataSourceFactory(context, "musicdao-audioplayer")
        val mediaItem = MediaItem.fromUri(uri)
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }


    fun playDownloadedTrack(
        track: Song,
        cover: File? = null
    ) {
        _playingTrack.value = track
        _coverFile.value = cover
        val mediaItem = MediaItem.fromUri(Uri.fromFile(track.file))
        Log.d("MusicDAOTorrent", "Trying to play ${track.file}")
        exoPlayer.playWhenReady = true
        exoPlayer.seekTo(0, 0)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun playDownloadingTrack(
        track: Song,
        context: Context,
        cover: File? = null
    ) {
        _playingTrack.value = track
        _coverFile.value = cover
        val mediaSource =
            buildMediaSource(Uri.fromFile(track.file), context)
        Log.d("MusicDAOTorrent", "Trying to play ${track.file}")
        exoPlayer.playWhenReady = true
        exoPlayer.seekTo(0, 0)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun release() {
        exoPlayer.release()
        exoPlayer.stop()
    }

    companion object {
        fun provideFactory(context: Context, database: CacheDatabase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(
                        context, database
                    ) as T
                }
            }
    }
}
