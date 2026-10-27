package com.oc.catemoji.catoc.ui.main.profile

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.databinding.ActivityProfileBinding
import com.oc.catemoji.catoc.databinding.ActivitySettingBinding
import com.oc.catemoji.catoc.databinding.ActivitySettingBinding.inflate
import com.oc.catemoji.catoc.ui.main.setting.SettingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class ProfileActivity : BaseActivity<ActivityProfileBinding, ProfileViewModel>(
    ActivityProfileBinding::inflate,
    ProfileViewModel::class.java
) {

    private val name = "name"
    override fun initView() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setTextActionBar(tvCenter, getString(R.string.profile))
        }
        binding.apply {
            txtCoinRank.isSelected = true
            txtName.isSelected = true
            txtNameData.isSelected = true
            txtTitle1.isSelected = true
            txtTitle2.isSelected = true
            txtTitle3.isSelected = true
        }
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { finish() }
        binding.apply {
            btnEditProfile.onClick {
                lifecycleScope.launch {
                    viewModelActivity.renameUser(name)
                }
            }
        }
    }
    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModelActivity.user.collect { user ->
                    user ?: return@collect

                    binding.apply {
                        txtNameData.text = user.name
                        txtCoinRank.text = user.rankbatter.toString()
                    }
                }
            }
        }
    }
}