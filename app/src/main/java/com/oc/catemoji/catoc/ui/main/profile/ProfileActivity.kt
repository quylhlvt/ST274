package com.oc.catemoji.catoc.ui.main.profile

import android.widget.Toast
import com.bumptech.glide.Glide
import com.facebook.shimmer.ShimmerDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.toEditProfile
import com.oc.catemoji.catoc.databinding.ActivityProfileBinding
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class ProfileActivity : BaseActivity<ActivityProfileBinding, ProfileViewModel>(
    ActivityProfileBinding::inflate,
    ProfileViewModel::class.java
) {

    private val statisticAdapter = ProfileStatisticAdapter()
    private val historyAdapter = ProfileHistoryAdapter()
    private val achievementAdapter = ProfileAchievementAdapter()

    override fun initView() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setTextActionBar(tvCenter, getString(R.string.profile))
        }
        binding.apply {
            recycleStatis.adapter = statisticAdapter
            recycleHistory.adapter = historyAdapter
            recycleAchi.adapter = achievementAdapter
            txtCoinRank.isSelected = true
            txtName.isSelected = true
            txtNameData.isSelected = true
            txtTitle1.isSelected = true
            txtTitle2.isSelected = true
            txtTitle3.isSelected = true
            imgAvatar.setImageDrawable(ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) })
            imgFrameAvatar.setImageDrawable(null)
        }
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { finish() }
        binding.apply {
            btnEditProfile.onClick {
                toEditProfile()
            }
        }
    }
    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.statistics.collect { items ->
                        if (statisticAdapter.items != items) statisticAdapter.submitList(items)
                    }
                }
                launch {
                    viewModel.history.collect { items ->
                        if (historyAdapter.items != items) historyAdapter.submitList(items)
                    }
                }
                launch {
                    viewModel.achievements.collect { items ->
                        if (achievementAdapter.items != items) achievementAdapter.submitList(items)
                    }
                }
                launch {
                    viewModel.loadFailed.collect { failed ->
                        if (failed) Toast.makeText(this@ProfileActivity,
                            R.string.profile_demo_load_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                viewModelActivity.user.collect { user ->
                    user ?: return@collect

                    binding.apply {
                        txtNameData.text = user.name
                        txtCoinRank.text = user.rankbatter.toString()
                        imgRank.setImageResource(com.oc.catemoji.catoc.utils.DataLocal.rankList[(user.rank - 1).coerceIn(0, 7)])
                        Glide.with(this@ProfileActivity).clear(imgAvatar)
                        loadImage(this@ProfileActivity, user.imageAvatar, imgAvatar)
                        Glide.with(this@ProfileActivity).clear(imgFrameAvatar)
                        imgFrameAvatar.setImageDrawable(null)
                        Glide.with(this@ProfileActivity).load(user.imageFrame.ifBlank { null })
                            .dontAnimate()
                            .into(imgFrameAvatar)
                    }
                }
            }
        }
    }
}
