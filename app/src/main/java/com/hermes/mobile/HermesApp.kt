package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.data.SessionStore

class HermesApp : Application() {
    val sessionStore: SessionStore by lazy { SessionStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: HermesApp
            private set
    }
}
