package com.ihaveapple.app

import android.app.Application
import android.content.Context
import com.ihaveapple.app.data.DramaRepository
import com.ihaveapple.app.data.LibraryStore

class AppContainer(context: Context) {
    val store = LibraryStore(context)
    val repository = DramaRepository(context)
}

class IhaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}