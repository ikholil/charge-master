package com.chargemaster.app.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chargemaster.app.R
import com.chargemaster.app.feature.settings.SettingsViewModel
import kotlinx.coroutines.launch

private data class Page(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: Int, val body: Int)

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val pages = listOf(
        Page(Icons.Outlined.BatteryChargingFull, R.string.onb_1_title, R.string.onb_1_body),
        Page(Icons.Outlined.Verified, R.string.onb_2_title, R.string.onb_2_body),
        Page(Icons.Outlined.NotificationsActive, R.string.onb_3_title, R.string.onb_3_body),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val finish = { vm.completeOnboarding(); onDone() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(168.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(stringResource(p.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(p.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val active = pager.currentPage == i
                Box(Modifier.padding(4.dp).size(width = if (active) 24.dp else 8.dp, height = 8.dp).background(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = finish) { Text(stringResource(R.string.skip)) }
            Spacer(Modifier.weight(1f))
            val last = pager.currentPage == pages.lastIndex
            Button(contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp), onClick = {
                if (!last) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else finish()
            }) { Text(stringResource(if (last) R.string.get_started else R.string.next)) }
        }
    }
}
