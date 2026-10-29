package com.family.kidtv

import android.app.Application
import com.family.kidtv.util.CrashReporter

class KidTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
