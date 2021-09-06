package com.example.lifecycledemo

import android.app.Application

/**
 *  author : Wang Xin
 *  date : 2021/9/2 16:29
 *  description :
 */
class APP :Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SensorsDataActivityLifecycleCallbacks())
    }
}