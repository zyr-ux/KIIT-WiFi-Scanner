package com.project.wifi_loc_protoype

import android.app.Application
import com.google.android.material.color.DynamicColors

class WifiLocApplication : Application(){
    override fun onCreate()
    {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)// Android 12+
    }
}