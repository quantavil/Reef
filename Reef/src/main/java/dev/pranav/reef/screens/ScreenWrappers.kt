package dev.pranav.reef.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pranav.reef.ui.appusage.AppUsageScreen
import dev.pranav.reef.ui.appusage.AppUsageStats
import dev.pranav.reef.ui.appusage.AppUsageViewModel
import dev.pranav.reef.ui.whitelist.WhitelistScreen
import dev.pranav.reef.ui.whitelist.WhitelistViewModel

@Composable
fun UsageScreenWrapper(
    context: Context,
    usageStatsManager: UsageStatsManager,
    launcherApps: LauncherApps,
    packageManager: PackageManager,
    currentPackageName: String,
    onBackPressed: () -> Unit,
    onAppClick: (AppUsageStats) -> Unit
) {
    val viewModel: AppUsageViewModel = viewModel(
        key = "app_usage_viewmodel",
        factory = object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>): T {
                return AppUsageViewModel(
                    context, usageStatsManager, launcherApps, packageManager, currentPackageName
                ) as T
            }
        }
    )

    AppUsageScreen(
        viewModel = viewModel,
        onBackPressed = onBackPressed,
        onAppClick = onAppClick
    )
}

@Composable
fun WhitelistScreenWrapper(
    launcherApps: LauncherApps,
    packageManager: PackageManager,
    currentPackageName: String
) {
    val viewModel: WhitelistViewModel = viewModel(
        factory = object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>): T {
                return WhitelistViewModel(
                    launcherApps, packageManager, currentPackageName
                ) as T
            }
        }
    )

    WhitelistScreen(
        uiState = viewModel.uiState.value,
        onToggle = viewModel::toggleWhitelist,
        searchQuery = viewModel.searchQuery.value,
        onSearchQueryChange = viewModel::onSearchQueryChange
    )
}
