package com.oc.catemoji.catoc.ui.main.setting

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import androidx.core.app.ShareCompat
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.core.helper.RateHelper
import com.oc.catemoji.catoc.databinding.ActivitySettingBinding
import com.oc.catemoji.catoc.utils.state.RateState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingActivity : BaseActivity<ActivitySettingBinding, SettingViewModel>(
    ActivitySettingBinding::inflate,
    SettingViewModel::class.java
) {
override fun initView() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.back_app)
            setTextActionBar(tvCenter, getString(R.string.settings))
        }
        binding.txt1.isSelected = true
        binding.txt2.isSelected = true
        binding.txt3.isSelected = true
        binding.txt4.isSelected = true
        if (sharedPreferences.isRateRequest()) binding.btnRate.gone()
        else binding.btnRate.visible()
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { finish() }
        binding.btnLang.onClick {
            startActivity(Intent(this, com.oc.catemoji.catoc.ui.language.LanguageActivity::class.java))
        }
        binding.btnPolicy.onClick {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(POLICY_URL)))
        }
        binding.btnRate.onClick {
            RateHelper.showRateDialog(this, sharedPreferences) { state ->
                if (state != RateState.CANCEL) {
                    binding.btnRate.gone()
                    android.widget.Toast.makeText(this, R.string.have_rated, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnShare.onClick {
            ShareCompat.IntentBuilder(this)
                .setType("text/plain")
                .setChooserTitle("Chooser title")
                .setText("http://play.google.com/store/apps/details?id=$packageName")
                .startChooser()
        }
    }

    companion object {
        private const val POLICY_URL = "https://sites.google.com/view/cat-avatar-cat-maker/home"
    }
}
