package dev.onelenyk.bringalive

import android.app.Application
import dev.onelenyk.bringalive.di.initKoin
import org.koin.android.ext.koin.androidContext

class BringaliveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@BringaliveApplication)
        }
    }
}
