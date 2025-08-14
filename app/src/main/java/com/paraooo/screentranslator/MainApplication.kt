package com.paraooo.screentranslator

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 앱이 시작될 때 OpenCV 라이브러리를 초기화하고 로드합니다.
        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "OpenCV is initialized successfully.")
        } else {
            Log.e("OpenCV", "OpenCV initialization failed.")
        }
    }
}