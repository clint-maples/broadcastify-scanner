package com.clintmaples.broadcastifyscanner

import android.app.Application

class ScannerApp : Application() {
    lateinit var controller: ScannerController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = ScannerController(this)
    }
}
