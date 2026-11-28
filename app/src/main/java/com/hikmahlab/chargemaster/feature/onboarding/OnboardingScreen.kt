package com.hikmahlab.chargemaster.feature.onboarding

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hikmahlab.chargemaster.core.design.Overline
import com.hikmahlab.chargemaster.core.design.LocalPastels
import androidx.compose.foundation.shape.CircleShape
import java.util.Locale
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
import com.hikmahlab.chargemaster.R
import com.hikmahlab.chargemaster.feature.settings.SettingsViewModel
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Overline(stringResource(R.string.app_name), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = finish) { Text(stringResource(R.string.skip), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize().padding(end = 8.dp), verticalArrangement = Arrangement.Center) {
                val pastel = LocalPastels.current.cycle[i % 3]
                Box(Modifier.size(132.dp).background(pastel.container, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, Modifier.size(56.dp), tint = pastel.content)
                }
                Spacer(Modifier.height(36.dp))
                Text(String.format(Locale.getDefault(), "%02d / %02d", i + 1, pages.size),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(p.title), style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 38.sp))
                Spacer(Modifier.height(18.dp))
                Text(stringResource(p.body), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size) { i ->
                    val active = pager.currentPage == i
                    Box(Modifier.width(if (active) 26.dp else 10.dp).height(10.dp).background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape))
                }
            }
            val last = pager.currentPage == pages.lastIndex
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                onClick = {
                    if (!last) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else finish()
                },
            ) { Text(stringResource(if (last) R.string.get_started else R.string.next)) }
        }
    }
}
