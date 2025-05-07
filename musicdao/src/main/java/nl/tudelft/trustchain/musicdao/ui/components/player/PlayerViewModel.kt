@file:Suppress("DEPRECATION")

package nl.tudelft.trustchain.musicdao.ui.components.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import nl.tudelft.trustchain.musicdao.core.repositories.model.Song
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.map
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album

class PlayerViewModel(context: Context, albumRepository: AlbumRepository) : ViewModel() {

    private val _playingTrack: MutableStateFlow<Song?> = MutableStateFlow(null)
    val playingTrack: StateFlow<Song?> = _playingTrack

    private val _coverFile: MutableStateFlow<File?> = MutableStateFlow(null)
    val coverFile: StateFlow<File?> = _coverFile

    val exoPlayer by lazy {
        ExoPlayer.Builder(context).build()
    }


    init {
        // ② now you can collect albums, pick a random one, and play it
        viewModelScope.launch {
            albumRepository.refreshCache()
            albumRepository
                .getAlbumsFlow()                  // LiveData<List<Album>>
                .asFlow()                         // Flow<List<Album>>
                .map { albums ->
                    // turn each emission into a Pair(albums, allSongs)
                    val allSongs = albums.flatMap { it.songs.orEmpty() }
                    albums to allSongs
                }
                // only continue once there *are* songs AND nothing is yet playing
                .filter { (_, allSongs) ->
                    val ready = allSongs.isNotEmpty() && _playingTrack.value == null
                    Log.d(
                        "Ioana",
                        "Filter check — hasSongs=${allSongs.isNotEmpty()}, " +
                            "noTrackYet=${_playingTrack.value == null} → $ready"
                    )
                    ready
                }
                // 4️⃣ suspend until that condition is first met
                .first()
                // 5️⃣ now pick & play your random track
                .let { (albums, allSongs) ->
                    Log.d("Ioana", "Songs are ready; launching random play")
                    val randomSong = allSongs.random()
                    // find its album so you can grab the cover
                    val cover = albums
                        .first { it.songs.orEmpty().contains(randomSong) }
                        .cover

                    playDownloadedTrack(randomSong, cover)
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
        fun provideFactory(context: Context, albumRepository: AlbumRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(
                        context, albumRepository
                    ) as T
                }
            }
    }
}
