package com.ledgerhub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ledgerhub.App
import com.ledgerhub.db.DatabaseDriverFactory
import com.ledgerhub.db.LedgerHubDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = LedgerHubDatabase(DatabaseDriverFactory(applicationContext).createDriver())
        setContent {
            App(database)
        }
    }
}
