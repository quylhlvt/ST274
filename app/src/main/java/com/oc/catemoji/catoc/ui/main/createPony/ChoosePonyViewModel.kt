package com.oc.catemoji.catoc.ui.main.createPony

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.databinding.FragmentChoosePonyBinding
import com.oc.catemoji.catoc.databinding.ItemChooseBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ── VIEWMODEL ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ChoosePonyViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) :
    ViewModel() {

    private val cacheFile = File(context.filesDir, "cached_templates.json")
    private val gson = Gson()

    private val _filteredTemplates = MutableStateFlow<List<CustomModel>>(emptyList())
    val filteredTemplates: StateFlow<List<CustomModel>> = _filteredTemplates.asStateFlow()

    private val _networkAvailable = MutableStateFlow(false)
    val networkAvailable: StateFlow<Boolean> = _networkAvailable.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkAvailable.value = true
        }
        override fun onLost(network: Network) {
            _networkAvailable.value = false
        }
    }

    init {
        _networkAvailable.value = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        if (_filteredTemplates.value.isEmpty()) loadFromCache()
    }

    fun updateFilteredTemplates(templates: List<CustomModel>, hasInternet: Boolean) {
        if (!hasInternet) {
            val offlineList = templates
                .filterNot { it.id.startsWith("online_") }
                .sortedBy { it.level }

            if (offlineList.isNotEmpty()) {
                _filteredTemplates.value = offlineList
            } else if (_filteredTemplates.value.isEmpty()) {
                loadFromCache()
            }
            return
        }

        if (templates.isNotEmpty()) {
            val sorted = templates.sortedBy { it.level }
            _filteredTemplates.value = sorted
            saveToCache(sorted)
        }
    }

    private fun saveToCache(templates: List<CustomModel>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cacheFile.writeText(gson.toJson(templates))
            } catch (e: Exception) {
                Log.e("DEBUG_PONY", "save cache failed: ${e.message}")
            }
        }
    }

    private fun loadFromCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!cacheFile.exists()) return@launch
                val type = object : TypeToken<List<CustomModel>>() {}.type
                val list = gson.fromJson<List<CustomModel>>(cacheFile.readText(), type) ?: emptyList()
                Log.d("DEBUG_PONY", "loaded ${list.size} from cache")
                _filteredTemplates.value = list.sortedBy { it.level }
            } catch (e: Exception) {
                Log.e("DEBUG_PONY", "load cache failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

}
