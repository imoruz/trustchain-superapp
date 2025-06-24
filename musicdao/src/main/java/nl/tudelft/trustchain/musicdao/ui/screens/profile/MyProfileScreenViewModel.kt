package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.core.dao.DaoCommunity
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import javax.inject.Inject

@HiltViewModel
class MyProfileScreenViewModel
    @Inject
    constructor(
        private val artistRepository: ArtistRepository,
        private val albumRepository: AlbumRepository,
        private val musicCommunity: MusicCommunity,
        private val daoCommunity: DaoCommunity
    ) : ViewModel() {

        private val _profile: MutableStateFlow<Artist?> = MutableStateFlow(null)
        var profile: StateFlow<Artist?> = _profile

        private val _peerAmount = MutableStateFlow(0)
        val peerAmount: StateFlow<Int> = _peerAmount

        private val _totalReleaseAmount: MutableLiveData<Int> = MutableLiveData()
        var totalReleaseAmount: LiveData<Int> = _totalReleaseAmount

        fun publicKey(): String {
            return musicCommunity.publicKeyHex()
        }

        suspend fun publishEdit(
            name: String,
            bitcoinAddress: String,
            socials: String,
            biography: String
        ): Boolean {
            return artistRepository.edit(name, bitcoinAddress, socials, biography)
        }

        init {
            viewModelScope.launch {
                profile = artistRepository.getArtistStateFlow(publicKey())
//                _peerAmount.value = musicCommunity.getPeers().size
                _peerAmount.value = daoCommunity.getPeers().size
                _totalReleaseAmount.value = albumRepository.getAlbums().size
            }
        }
        fun refreshPeers() {
            viewModelScope.launch {
                _peerAmount.value = musicCommunity.getPeers().size
            }
        }

    }
