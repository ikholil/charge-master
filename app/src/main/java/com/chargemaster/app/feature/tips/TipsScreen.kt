package com.chargemaster.app.feature.tips

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
import com.chargemaster.app.R
import com.chargemaster.app.core.design.AppCard
import com.chargemaster.app.core.design.BatteryTips
import com.chargemaster.app.core.design.IconBadge
import com.chargemaster.app.core.design.InfoBanner
import com.chargemaster.app.core.design.ScreenScaffold
import com.chargemaster.app.core.design.Tokens

/** Static battery-care guide. Content is general lithium-ion guidance, not device-specific. */
@Composable
fun TipsScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.tips_title), onBack = onBack) {
        item {
            Text(stringResource(R.string.tips_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier)
        }
        itemsIndexed(BatteryTips) { i, tip ->
            val tint = if (i % 2 == 0) MaterialTheme.colorScheme.primary else Tokens.Info
            AppCard {
                Row(verticalAlignment = Alignment.Top) {
                    IconBadge(tip.icon, tint = tint, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(tip.title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(tip.body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { InfoBanner(stringResource(R.string.no_charge_control)) }
    }
}
