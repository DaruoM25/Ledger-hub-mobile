package com.ledgerhub

import androidx.compose.ui.window.ComposeUIViewController
import com.ledgerhub.db.DatabaseDriverFactory
import com.ledgerhub.db.LedgerHubDatabase
import platform.UIKit.UIViewController

/** Pont KMP -> iOS : instancie le UIViewController hébergeant l'UI Compose. */
fun MainViewController(): UIViewController {
    val database = LedgerHubDatabase(DatabaseDriverFactory().createDriver())
    return ComposeUIViewController { App(database) }
}
