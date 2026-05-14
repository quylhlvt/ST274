package com.oc.catemoji.catoc.ui.main.createPony

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.core.base.BaseFragment
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.databinding.FragmentChoosePonyBinding
import com.oc.catemoji.catoc.databinding.ItemChooseBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── VIEWMODEL ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ChoosePonyViewModel @Inject constructor() : ViewModel()
// Template data đến từ shared ViewModelActivity – không cần logic riêng ở đây.

// ── ADAPTER ───────────────────────────────────────────────────────────────────


// ── FRAGMENT ──────────────────────────────────────────────────────────────────
