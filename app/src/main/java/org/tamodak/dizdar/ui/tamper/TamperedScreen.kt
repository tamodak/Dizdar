package org.tamodak.dizdar.ui.tamper

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tamodak.dizdar.R
import org.tamodak.dizdar.admin.DizdarDeviceAdminReceiver
import org.tamodak.dizdar.core.DizdarLog
import org.tamodak.dizdar.ui.components.DizdarBody
import org.tamodak.dizdar.ui.components.DizdarButton
import org.tamodak.dizdar.ui.components.DizdarGutter
import org.tamodak.dizdar.ui.components.DizdarScreen
import org.tamodak.dizdar.ui.theme.DizdarForeground
import org.tamodak.dizdar.ui.theme.DizdarIcons
import org.tamodak.dizdar.ui.theme.DizdarRed
import org.tamodak.dizdar.ui.theme.DizdarSurface

/**
 * Shown when apps are still blocked but the passkey can no longer be verified — either it is
 * missing entirely, or the Keystore key that peppered its hash was dropped by "Clear data".
 *
 * Dizdar deliberately refuses to unblock anything here. Falling back to "no passkey means anyone
 * can get in" would hand the apps to whoever cleared the data, which is exactly the attack.
 *
 * The only way out is therefore from a computer, removing the admin over adb — which is why this
 * screen is nothing but that command and a copy button. It is a dead end by design: if there were
 * an in-app escape, it would be the bypass.
 */
@Composable
fun TamperedScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val command = remember(context) {
        "adb shell dpm remove-active-admin " +
            DizdarDeviceAdminReceiver.componentName(context).flattenToString()
    }

    DizdarScreen(title = stringResource(R.string.tamper_title), modifier = modifier) {
        Column(
            modifier = Modifier.padding(
                start = DizdarGutter,
                end = DizdarGutter,
                top = 28.dp,
                bottom = 40.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Icon(
                imageVector = DizdarIcons.Warning,
                contentDescription = null,
                tint = DizdarRed,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.tamper_title),
                style = MaterialTheme.typography.titleLarge,
                color = DizdarRed,
                textAlign = TextAlign.Center,
            )
            DizdarBody(
                text = stringResource(R.string.tamper_body),
                color = DizdarForeground,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DizdarSurface)
                    .padding(16.dp),
            ) {
                Text(
                    text = command,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = DizdarForeground,
                )
            }
            DizdarButton(
                text = stringResource(R.string.copy),
                onClick = {
                    DizdarLog.i(DizdarLog.UI, "Recovery command copied from the tampered screen")
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Dizdar", command))
                },
            )
        }
    }
}
