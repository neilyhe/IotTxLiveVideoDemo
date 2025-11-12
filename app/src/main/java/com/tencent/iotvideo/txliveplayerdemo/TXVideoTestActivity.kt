package com.tencent.iotvideo.txliveplayerdemo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioFormat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.tencent.iot.video.link.util.audio.AudioRecordUtil
import com.tencent.iotvideo.txliveplayerdemo.databinding.ActivityTxVideoTestBinding
import com.tencent.iotvideo.txliveplayerdemo.utils.StatusBarUtil
import com.tencent.rtmp.ITXLivePlayListener
import com.tencent.rtmp.TXLiveBase
import com.tencent.rtmp.TXLiveBaseListener
import com.tencent.rtmp.TXLivePlayConfig
import com.tencent.rtmp.TXLivePlayer
import com.tencent.rtmp.TXLivePlayer.PLAY_TYPE_LIVE_FLV
import com.tencent.ugc.TXRecordCommon
import com.tencent.ugc.TXRecordCommon.RECORD_TYPE_STREAM_SOURCE
import com.tencent.xnet.XP2P
import com.tencent.xnet.XP2PAppConfig
import com.tencent.xnet.XP2PCallback
import com.tencent.xnet.annotations.XP2PProtocolType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedList

private val TAG = TXVideoTestActivity::class.simpleName

class TXVideoTestActivity : ComponentActivity(), XP2PCallback, CoroutineScope by MainScope() {

    private val tag = TXVideoTestActivity::class.simpleName

    private var productId: String = ""
    private var deviceName: String = ""
    private var xp2pInfo: String = ""
    private val channel: Int = 0
    private var urlPrefix = ""
    private var audioRecordUtil: AudioRecordUtil? = null

    private var connectStartTime = 0L
    private var connectTime = 0L
    private var startShowVideoTime = 0L

    private var screenWidth = 0
    private var screenHeight = 0

    private val mLivePlayer: TXLivePlayer by lazy { TXLivePlayer(this@TXVideoTestActivity) }
    private val xP2PAppConfig = XP2PAppConfig().also { appConfig ->
        appConfig.appKey =
            BuildConfig.TencentIotLinkSDKDemoAppkey //为explorer平台注册的应用信息(https://console.cloud.tencent.com/iotexplorer/v2/instance/app/detai) explorer控制台- 应用开发 - 选对应的应用下的 appkey/appsecret
        appConfig.appSecret =
            BuildConfig.TencentIotLinkSDKDemoAppSecret //为explorer平台注册的应用信息(https://console.cloud.tencent.com/iotexplorer/v2/instance/app/detai) explorer控制台- 应用开发 - 选对应的应用下的 appkey/appsecret
        appConfig.autoConfigFromDevice = true
        appConfig.type = XP2PProtocolType.XP2P_PROTOCOL_AUTO
    }

    private val binding: ActivityTxVideoTestBinding by lazy {
        ActivityTxVideoTestBinding.inflate(layoutInflater)
    }

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

    private fun initView() {
        productId = intent.getStringExtra("productId") ?: ""
        deviceName = intent.getStringExtra("deviceName") ?: ""
        xp2pInfo = intent.getStringExtra("p2pInfo") ?: ""
        val appKey = intent.getStringExtra("appKey") ?: ""
        val appSecret = intent.getStringExtra("appSecret") ?: ""
        xP2PAppConfig.appKey = appKey
        xP2PAppConfig.appSecret = appSecret
        xP2PAppConfig.autoConfigFromDevice = intent.getBooleanExtra("isStartCross", false)
        val protocol = intent.getStringExtra("protocol") ?: "auto"
        if (protocol == "udp") {
            xP2PAppConfig.type = XP2PProtocolType.XP2P_PROTOCOL_UDP
        } else if (protocol == "tcp") {
            xP2PAppConfig.type = XP2PProtocolType.XP2P_PROTOCOL_TCP
        } else {
            xP2PAppConfig.type = XP2PProtocolType.XP2P_PROTOCOL_AUTO
        }

        binding.vTitle.tvTitle.text = deviceName
        binding.vTitle.ivBack.setOnClickListener {
            onBackPressed()
        }
        binding.tvVideoQuality.text = "高清"

        audioRecordUtil = AudioRecordUtil(
            this,
            "${productId}/${deviceName}",
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        XP2P.setCallback(this)
        val filaPath =
            getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath + "/data_video.flv"
        XP2P.recordstreamPath(filaPath) //自定义采集裸流路径
        val wm = this.getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getMetrics(dm)
        val width = dm.widthPixels // 屏幕宽度（像素）
        val height = dm.heightPixels // 屏幕高度（像素）
        val density = dm.density // 屏幕密度（0.75 / 1.0 / 1.5）
        screenWidth = (width / density).toInt() // 屏幕宽度(dp)
        screenHeight = (height / density).toInt() // 屏幕高度(dp)

        //视立方的测试licence，有效期14天
        val licenceURL = ""
        val licenceKey = ""
        initTXLivePlayer(licenceURL, licenceKey)

        startService()
    }

    // 初始化TXLivePlayer
    private fun initTXLivePlayer(licenceURL: String, licenceKey: String) {
        TXLiveBase.getInstance().setLicence(this@TXVideoTestActivity, licenceURL, licenceKey)
        TXLiveBase.setListener(object : TXLiveBaseListener() {
            override fun onLicenceLoaded(result: Int, reason: String) {
                Log.i(TAG, "onLicenceLoaded: result:$result, reason:$reason")
            }
        })
        val config = TXLivePlayConfig()
        config.cacheTime = 1f
        config.maxAutoAdjustCacheTime = 2f
        mLivePlayer.setConfig(config)
        mLivePlayer.setPlayerView(binding.vPreview)
        mLivePlayer.setPlayListener(object : ITXLivePlayListener {
            override fun onPlayEvent(p0: Int, p1: Bundle?) {}

            override fun onNetStatus(p0: Bundle?) {}

        })
        //录制屏幕监听回调
        mLivePlayer.setVideoRecordListener(object : TXRecordCommon.ITXVideoRecordListener {
            override fun onRecordEvent(p0: Int, p1: Bundle?) {}

            override fun onRecordProgress(p0: Long) {}

            //录制完成回调 ，p0?.videoPath为录制的路径
            override fun onRecordComplete(p0: TXRecordCommon.TXRecordResult?) {
                Log.d(
                    tag,
                    "p0:${p0?.videoPath}   ${p0?.coverPath}  ${p0?.retCode}   ${p0?.descMsg}"
                )
            }

        })
    }


    private fun startService() {
        XP2P.startService(
            this, productId, deviceName, xp2pInfo, xP2PAppConfig
        )
    }

    private fun restartService() {
        val id = "${productId}/${deviceName}"
        XP2P.stopService(id)
        startService()
    }

    private fun checkDeviceState() {
        Log.d(tag, "====检测设备状态===")
        launch(Dispatchers.IO) {
            getDeviceStatus("${productId}/${deviceName}") { isOnline, msg ->
                launch(Dispatchers.Main) {
                    Toast.makeText(this@TXVideoTestActivity, msg, Toast.LENGTH_SHORT).show()
                    if (isOnline) {
                        delegateHttpFlv()
                    } else {
                        restartService()
                    }
                }
            }
        }
    }

    private fun delegateHttpFlv() {
        val id = "${productId}/${deviceName}"
//        XP2P.recordstream(id) //开启自定义采集裸流
        val prefix = XP2P.delegateHttpFlv(id)
        if (prefix.isNotEmpty()) {
            urlPrefix = prefix
            resetPlayer()
        } else {
            Toast.makeText(this, "get urlPrefix is empty", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun setListener() {
        binding.radioRecord.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                val ret = mLivePlayer.startRecord(RECORD_TYPE_STREAM_SOURCE)
                if (ret != 0) {
                    Toast.makeText(
                        this@TXVideoTestActivity,
                        "该音频格式不支持录像",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.radioRecord.isChecked = false
                }
            } else {
                mLivePlayer.stopRecord()
            }
        }
        binding.radioPhoto.setOnClickListener {
            mLivePlayer.snapshot { p0 -> saveBitmap(this@TXVideoTestActivity, p0) }
        }
    }

    private fun resetPlayer() {

        val url = when (binding.tvVideoQuality.text.toString()) {
            getString(R.string.video_quality_high_str) -> "ipc.flv?action=live&channel=${channel}&quality=super"

            getString(R.string.video_quality_medium_str) -> "ipc.flv?action=live&channel=${channel}&quality=high"

            getString(R.string.video_quality_low_str) -> "ipc.flv?action=live&channel=${channel}&quality=standard"

            else -> ""
        }
        play(urlPrefix + url)
    }

    private fun play(flvUrl: String) {
        Log.e(tag, "play flvUrl:$flvUrl")
        mLivePlayer.startLivePlay(flvUrl, PLAY_TYPE_LIVE_FLV)
    }

    override fun fail(msg: String?, errorCode: Int) {}

    override fun commandRequest(id: String?, msg: String?) {
        Log.e(tag, "xp2pEventNotify id:$id  msg:$msg")
    }

    override fun xp2pEventNotify(id: String?, msg: String?, event: Int) {
        Log.e(tag, "xp2pEventNotify id:$id  msg:$msg  event:$event")
        if (event == 1003) {
            Log.e(tag, "====event === 1003")
            startShowVideoTime = 0L
            launch(Dispatchers.Main) {
                val content = getString(R.string.disconnected_and_reconnecting, id)
                Toast.makeText(this@TXVideoTestActivity, content, Toast.LENGTH_SHORT).show()
                restartService()
            }
        } else if (event == 1004 || event == 1005) {
            connectTime = System.currentTimeMillis() - connectStartTime
            if (event == 1004) {
                Log.e(tag, "====event === 1004")
                checkDeviceState()
            }
        } else if (event == 1010) {
            Log.e(tag, "====event === 1010, 校验失败，info撞库防止串流： $msg")
        }
    }

    override fun avDataRecvHandle(id: String?, data: ByteArray?, len: Int) {
        Log.e(tag, "avDataRecvHandle id:$id  data:$data  len:$data")
    }

    override fun avDataCloseHandle(id: String?, msg: String?, errorCode: Int) {
        Log.e(tag, "avDataCloseHandle id:$id  msg:$msg  errorCode:$errorCode")
    }

    override fun onDeviceMsgArrived(id: String?, data: ByteArray?, len: Int): String {
        Log.e(tag, "onDeviceMsgArrived id:$id  data:$data  len:$len")
        val reply = JSONObject()
        reply.put("code", "0")
        reply.put("msg", "test command reply")
        return reply.toString()
    }

    private fun getDeviceStatus(id: String?, block: ((Boolean, String) -> Unit)? = null) {
        var command: ByteArray? = null
        when (binding.tvVideoQuality.text.toString()) {
            getString(R.string.video_quality_high_str) -> {
                command =
                    "action=inner_define&channel=0&cmd=get_device_st&type=live&quality=super".toByteArray()
            }

            getString(R.string.video_quality_medium_str) -> {
                command =
                    "action=inner_define&channel=0&cmd=get_device_st&type=live&quality=high".toByteArray()
            }

            getString(R.string.video_quality_low_str) -> {
                command =
                    "action=inner_define&channel=0&cmd=get_device_st&type=live&quality=standard".toByteArray()
            }
        }
        val reponse =
            XP2P.postCommandRequestSync(id, command, command!!.size.toLong(), 2 * 1000 * 1000)
        if (!TextUtils.isEmpty(reponse)) {
//            val deviceStatuses: List<DeviceStatus> =
//                JSONArray.parseArray(reponse, DeviceStatus::class.java)
            // 0   接收请求
            // 1   拒绝请求
            // 404 error request message
            // 405 connect number too many
            // 406 current command don't support
            // 407 device process error
//            var deviceState: Int = -1
//            var msg: String = ""
//            if (deviceStatuses.isNotEmpty()) {
//                msg = when (deviceStatuses[0].status) {
//                    0 -> "设备状态正常"
//                    404 -> "设备状态异常, error request message: $reponse"
//                    405 -> "设备状态异常, connect number too many: $reponse"
//                    406 -> "设备状态异常, current command don't support: $reponse"
//                    407 -> "设备状态异常, device process error: $reponse"
//                    else -> "设备状态异常, 拒绝请求: $reponse"
//                }
//                deviceState = deviceStatuses[0].status
//            } else {
//                msg = "获取设备状态失败"
//            }
            block?.invoke(true, "获取设备状态成功")
        } else {
            block?.invoke(false, "获取设备状态失败")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        finishPlayer()
        XP2P.stopService("${productId}/${deviceName}")
        XP2P.setCallback(null)
        cancel()
    }

    private fun finishPlayer() {
        if (binding.radioRecord.isChecked) {
            mLivePlayer.stopRecord()
        }
        mLivePlayer.stopPlay(false)
    }
}


fun saveBitmap(bitmap: Bitmap): File {
    return saveBitmap(null, bitmap)
}

fun saveBitmap(context: Context?, bitmap: Bitmap): File {
    val file = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
        "${System.currentTimeMillis()}.jpg"
    )
    return saveBitmap(context, bitmap, file.absolutePath)
}

private val fileList = LinkedList<File>()

fun saveBitmap(context: Context?, bitmap: Bitmap, path: String): File {
    val file = File(path)
    BufferedOutputStream(FileOutputStream(file)).run {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, this)
        flush()
        close()
    }
    fileList.add(file)

    context?.let {
        if (!file.exists()) return@let

        val values = ContentValues()
        values.put(MediaStore.Images.Media.DATA, file.absolutePath)
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        it.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        it.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
    }
    return file
}