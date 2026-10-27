package com.oc.catemoji.catoc.ui.main.profile

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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


@AndroidEntryPoint
class ProfileActivity : BaseActivity<ActivityProfileBinding, SettingViewModel>(
    ActivityProfileBinding::inflate,
    SettingViewModel::class.java
) {
    override fun initView() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setTextActionBar(tvCenter, getString(R.string.profile))
        }
        binding.apply {
            txtCoinRank.isSelected = true
            txtName.isSelected = true
            txtNameData.isSelected = true
        }
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { finish() }
    }
}