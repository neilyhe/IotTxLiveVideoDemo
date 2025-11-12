package com.tencent.iotvideo.txliveplayerdemo

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.RadioButton
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.tencent.iotvideo.txliveplayerdemo.databinding.ActivityVideoTestInputBinding
import com.tencent.iotvideo.txliveplayerdemo.utils.StatusBarUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

class VideoTestInputActivity : ComponentActivity(),
    CoroutineScope by MainScope() {

    private var isStartCross = false
    private var protocol = "auto"

    private val binding by lazy { ActivityVideoTestInputBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkStyle()
        setContentView(binding.root)
        initView()
        setListener()
    }

    private fun checkStyle() {
        StatusBarUtil.setRootViewFitsSystemWindows(this, false)
        StatusBarUtil.setTranslucentStatus(this)
        if (!StatusBarUtil.setStatusBarDarkTheme(this, true)) {
            StatusBarUtil.setStatusBarColor(this, 0x55000000)
        }
    }

    fun initView() {
        with(binding) {
            vTitle.tvTitle.setText(R.string.iot_test_demo_name)
            productIdLayout.tvTip.setText(R.string.product_id_text)
            deviceNameLayout.tvTip.setText(R.string.device_name_text)
            p2pInfoLayout.tvTip.setText(R.string.p2p_info_text)
            appKeyLayout.tvTip.setText(R.string.app_key_text)
            appSecretLayout.tvTip.setText(R.string.app_secret)
            productIdLayout.evContent.setText("")

            productIdLayout.evContent.setHint(R.string.hint_product_id)
            productIdLayout.evContent.inputType = InputType.TYPE_CLASS_TEXT
            deviceNameLayout.evContent.setText("")

            deviceNameLayout.evContent.setHint(R.string.hint_device_name)
            deviceNameLayout.evContent.inputType = InputType.TYPE_CLASS_TEXT
            p2pInfoLayout.evContent.setText("")

            p2pInfoLayout.evContent.setHint(R.string.hint_p2p_info)
            p2pInfoLayout.evContent.inputType = InputType.TYPE_CLASS_TEXT
            appKeyLayout.evContent.setText("")

            appKeyLayout.evContent.setHint(R.string.hint_app_key)
            appKeyLayout.evContent.inputType = InputType.TYPE_CLASS_TEXT
            appSecretLayout.evContent.setText("")

            appSecretLayout.evContent.setHint(R.string.hint_app_secret)
            appSecretLayout.evContent.inputType = InputType.TYPE_CLASS_TEXT
        }
    }

    private fun setListener() {
        with(binding) {
            vTitle.ivBack.setOnClickListener { finish() }
            btnLogin.setOnClickListener(loginClickedListener)
            btnPaste.setOnClickListener {
                val clipboard = ContextCompat.getSystemService(
                    this@VideoTestInputActivity,
                    ClipboardManager::class.java
                )
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    clipboard.primaryClip?.getItemAt(0)?.text.toString().split("\n")
                        .forEachIndexed { index, s ->
                            when (index) {
                                0 -> productIdLayout.evContent.setText(s)
                                1 -> deviceNameLayout.evContent.setText(s)
                                2 -> p2pInfoLayout.evContent.setText(s)
                                3 -> appKeyLayout.evContent.setText(s)
                                4 -> appSecretLayout.evContent.setText(s)
                            }
                        }
                }
            }
            btnAppPaste.setOnClickListener {
                val clipboard = ContextCompat.getSystemService(
                    this@VideoTestInputActivity,
                    ClipboardManager::class.java
                )
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    clipboard.primaryClip?.getItemAt(0)?.text.toString().split("\n")
                        .forEachIndexed { index, s ->
                            when (index) {
                                0 -> appKeyLayout.evContent.setText(s)
                                1 -> appSecretLayout.evContent.setText(s)
                            }
                        }
                }
            }
            swtCross.setOnCheckedChangeListener { _, checked ->
                isStartCross = checked
                btnAppPaste.isVisible = checked
                appKeyLayout.root.isVisible = checked
                appSecretLayout.root.isVisible = checked
            }
            rgProtocol.setOnCheckedChangeListener { group, checkedId ->
                protocol = group.findViewById<RadioButton>(checkedId).tag.toString()
            }
        }
    }

    private var loginClickedListener = View.OnClickListener {
        with(binding) {
            val intent = Intent(this@VideoTestInputActivity, TXVideoTestActivity::class.java)
            intent.putExtra("productId", productIdLayout.evContent.text.toString())
            intent.putExtra("deviceName", deviceNameLayout.evContent.text.toString())
            intent.putExtra("p2pInfo", p2pInfoLayout.evContent.text.toString())
            intent.putExtra("appKey", appKeyLayout.evContent.text.toString())
            intent.putExtra("appSecret", appSecretLayout.evContent.text.toString())
            intent.putExtra("isStartCross", isStartCross)
            intent.putExtra("protocol", protocol)
            startActivity(intent)
        }
    }
}
