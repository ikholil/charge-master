package com.hikmahlab.chargemaster.feature.tips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hikmahlab.chargemaster.R
import com.hikmahlab.chargemaster.core.design.AppCard
import com.hikmahlab.chargemaster.core.design.BatteryTips
import com.hikmahlab.chargemaster.core.design.IconBadge
import com.hikmahlab.chargemaster.core.design.LocalPastels
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.hikmahlab.chargemaster.core.design.InfoBanner
import com.hikmahlab.chargemaster.core.design.ScreenScaffold

/** Static battery-care guide. Content is general lithium-ion guidance, not device-specific. */
@Composable
fun TipsScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.tips_title), onBack = onBack) {
        item {
            AppCard(containerColor = LocalPastels.current.mint.container) {
                Text(stringResource(R.string.tips_intro), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = LocalPastels.current.mint.content)
            }
        }
        itemsIndexed(BatteryTips) { i, tip ->
            val pastel = LocalPastels.current.cycle[i % LocalPastels.current.cycle.size]
            AppCard(contentPadding = 18.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    IconBadge(tip.icon, tint = pastel.content, container = pastel.container, size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(tip.title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(tip.body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { InfoBanner(stringResource(R.string.no_charge_control)) }
    }
}
