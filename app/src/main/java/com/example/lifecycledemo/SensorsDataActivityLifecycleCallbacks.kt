package com.dofun.libcommon.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import org.json.JSONObject
import kotlin.math.abs

/**
 *  author : Wang Xin
 *  date : 2021/9/2 16:10
 *  description :用于统计APP启动和退出事件
 */
class SensorsDataActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    private val TAG = "SensorsDataActivityLifecycleCallbacks"

    private lateinit var mActivity: Activity
    private var mActivityCount = 0
    private var mStartTimerCount = 0
    private var mHandler: Handler? = null

    private var onBackgroundTime = 0L //回到前台时间
    private var timerTagData = "" //打点计时标记，存储在本地。作为判断是否需要重试补发App退出的标记
    private val TRACK_TIMER = "track_timer"

    private val SESSION_INTERVAL_TIME = 6 * 1000L // 如果在 30s 之内没有新的页面打开，我们就认为 App 进入后台了，此时会触发 App 退出事件
    private val TIME_INTERVAL = 2000L //打点计时间隔

    private val MESSAGE_CODE_APP_END = 0
    private val MESSAGE_CODE_TIMER = 300
    init {
        initHandler()
    }


    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        this.mActivity = activity
    }

    override fun onActivityStarted(activity: Activity) {
        mHandler?.removeMessages(MESSAGE_CODE_APP_END)
        ++mActivityCount
        if (mActivityCount == 1) {
            if (isSessionTimeOut()) {
                mHandler?.sendEmptyMessage(MESSAGE_CODE_APP_END)
            }

            if (isSessionTimeOut()) {
                trackAppStart()
            }
        }

        if (mStartTimerCount++ == 0) {
            /*
                 * 在启动的时候开启打点，退出时停止打点，在此处可以防止两点：
                 *  1. App 在 onResume 之前 Crash，导致只有启动没有退出；
                 *  2. 多进程的情况下只会开启一个打点器；
                 */
            mHandler?.sendEmptyMessage(MESSAGE_CODE_TIMER)
        }

    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
        // 停止计时器，针对跨进程的情况，要停止当前进程的打点器
        mStartTimerCount--
        if (mStartTimerCount == 0) {
            mHandler!!.removeMessages(MESSAGE_CODE_TIMER)
        }


        mActivityCount = if (mActivityCount > 0) --mActivityCount else 0
        if (mActivityCount <= 0) {
            mHandler?.sendEmptyMessageDelayed(MESSAGE_CODE_APP_END, SESSION_INTERVAL_TIME)
            onBackgroundTime = System.currentTimeMillis()
        }

    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    private fun initHandler() {
        mHandler = Handler(Looper.getMainLooper(), Handler.Callback {
            when (it.what) {
                MESSAGE_CODE_TIMER -> {
                    generateTimeData()
                    if (mStartTimerCount > 0) {
                        mHandler!!.sendEmptyMessageDelayed(
                            MESSAGE_CODE_TIMER, TIME_INTERVAL
                        )
                    }
                }

                MESSAGE_CODE_APP_END -> {
                    trackAppEnd()
                }
            }
            true
        })
    }

    private fun generateTimeData() {
        val jsonObject = JSONObject()
        val curTime = System.currentTimeMillis()
        jsonObject.put(TRACK_TIMER, curTime)
        onBackgroundTime = curTime
        timerTagData = jsonObject.toString()
        mActivity.getSharedPreferences("vietnam_sensors", Context.MODE_PRIVATE).edit().putString(
            "timeTag",
            timerTagData
        ).apply()
    }

    // 触发 App 启动事件
    private fun trackAppStart() {
        Log.e(TAG, "App 启动")
    }

    // 触发 App 退出事件
    private fun trackAppEnd() {
        if (!TextUtils.isEmpty(timerTagData)) {
            Log.e(TAG, "App 退出")
            timerTagData = ""
            mActivity.getSharedPreferences("vietnam_sensors", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    private fun isSessionTimeOut(): Boolean {
        var endTrackTime = 0L
        timerTagData = mActivity.getSharedPreferences("vietnam_sensors", Context.MODE_PRIVATE).getString(
            "timeTag",
            ""
        ) ?: ""

        if(!TextUtils.isEmpty(timerTagData)){
            val jsonObject = JSONObject(timerTagData)
            endTrackTime = jsonObject.optLong(TRACK_TIMER)
        }

        if(endTrackTime == 0L){
            endTrackTime = onBackgroundTime
        }
        val currentTime = System.currentTimeMillis().coerceAtLeast(946656000000L)
        return abs(currentTime - endTrackTime) > SESSION_INTERVAL_TIME;
    }
}