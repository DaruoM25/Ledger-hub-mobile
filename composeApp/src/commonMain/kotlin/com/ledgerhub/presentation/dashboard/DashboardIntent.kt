package com.ledgerhub.presentation.dashboard

sealed interface DashboardIntent {
    data object LoadDashboard : DashboardIntent
}
