package com.wolfwheels.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wolfwheels.app.data.Prefs
import com.wolfwheels.app.ui.App
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.wolfwheels.app.data.ErrorLog.init(this)
        Prefs.init(this)
        com.wolfwheels.app.data.Wiki.init(this)
        com.wolfwheels.app.data.Wiki.trimCache()
        com.wolfwheels.app.data.Recents.init(this)
        com.wolfwheels.app.data.Account.init()
        com.wolfwheels.app.data.MyCollection.init(this)
        com.wolfwheels.app.data.Translator.init(this)
        enableEdgeToEdge()
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        // Bring changes made on other phones
        lifecycleScope.launch { com.wolfwheels.app.data.MyCollection.sync() }
        com.wolfwheels.app.ui.AppState.resumes.intValue++
    }
}
