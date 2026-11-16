package dev.blaze.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.ui.AmpActions
import dev.blaze.controller.ui.HomeStatus
import dev.blaze.controller.ui.HomeUiState
import dev.blaze.controller.ui.SystemActions
import dev.blaze.controller.ui.components.Panel
import dev.blaze.controller.ui.components.PrimaryButton
import dev.blaze.controller.ui.theme.Pb

/** Why the amp is not usable right now, with the one action that helps (permission, Bluetooth settings, retry, pick a device). */
@Composable
fun ConnectionCard(ui: HomeUiState, actions: AmpActions, system: SystemActions, modifier: Modifier = Modifier) {
    // solid backing: the card sits on top of the dimmed controls and must hide them completely
    Box(modifier.background(Color(0xFF111114), RoundedCornerShape(14.dp))) {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(statusMessage(ui.status), color = Color(0xFFE4E4E8), fontSize = 13.sp)
                when (ui.status) {
                    HomeStatus.NeedsPermission ->
                        if (ui.permissionNeedsSettings) PrimaryButton("Ayarlardan Bluetooth iznini ver", system.onOpenAppSettings)
                        else PrimaryButton("Bluetooth iznini ver", system.onGrantPermission)
                    HomeStatus.BluetoothOff -> PrimaryButton("Bluetooth ayarlarını aç", system.onOpenBluetoothSettings)
                    HomeStatus.NotFound, HomeStatus.GaveUp -> PrimaryButton("Tekrar ara", actions::retry)
                    HomeStatus.LinkLost -> PrimaryButton("Tekrar bağlan", actions::retry)
                    HomeStatus.PickDevice -> ui.devices.forEach { device ->
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .testTag("device-${device.address}")
                                .heightIn(min = 48.dp)
                                .clip(shape)
                                .background(Color(0xFF1A1A1F))
                                .border(1.dp, Pb.Red, shape)
                                .clickable { actions.onDevicePicked(device) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${device.name}  ${device.address}", color = Color(0xFFEDEDF0), fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun statusMessage(s: HomeStatus) = when (s) {
    HomeStatus.Starting -> "Başlatılıyor…"
    HomeStatus.NeedsPermission -> "Amfiyi bulmak için Bluetooth izni gerekli."
    HomeStatus.BluetoothOff -> "Bluetooth kapalı."
    HomeStatus.Scanning -> "Amfi aranıyor…"
    HomeStatus.NotFound -> "Amfi bulunamadı, aramaya devam ediliyor…"
    HomeStatus.GaveUp -> "Amfi bulunamadı. Açık ve yakında olduğundan emin ol, sonra tekrar ara."
    HomeStatus.PickDevice -> "Birden fazla Blaze bulundu, birini seç:"
    HomeStatus.Connecting -> "Bağlanıyor…"
    HomeStatus.Connected -> ""
    HomeStatus.Reconnecting -> "Bağlantı koptu, yeniden bağlanılıyor…"
    HomeStatus.LinkLost -> "Bağlantı koptu ve amfiye ulaşılamıyor. Amfinin açık olduğundan emin ol."
}
