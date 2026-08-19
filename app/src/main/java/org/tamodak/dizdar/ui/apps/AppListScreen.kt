package org.tamodak.dizdar.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tamodak.dizdar.R
import org.tamodak.dizdar.data.AppEntry
import org.tamodak.dizdar.ui.DizdarUiState
import org.tamodak.dizdar.ui.components.DizdarBody
import org.tamodak.dizdar.ui.components.DizdarCheckbox
import org.tamodak.dizdar.ui.components.DizdarDialog
import org.tamodak.dizdar.ui.components.DizdarField
import org.tamodak.dizdar.ui.components.DizdarGutter
import org.tamodak.dizdar.ui.components.DizdarIconBox
import org.tamodak.dizdar.ui.components.DizdarPanel
import org.tamodak.dizdar.ui.components.DizdarPrimaryButton
import org.tamodak.dizdar.ui.components.DizdarRule
import org.tamodak.dizdar.ui.components.DizdarScreen
import org.tamodak.dizdar.ui.components.DizdarTabs
import org.tamodak.dizdar.ui.components.DizdarToggleRow
import org.tamodak.dizdar.ui.theme.DizdarBackground
import org.tamodak.dizdar.ui.theme.DizdarDivider
import org.tamodak.dizdar.ui.theme.DizdarForeground
import org.tamodak.dizdar.ui.theme.DizdarGreen
import org.tamodak.dizdar.ui.theme.DizdarIcons
import org.tamodak.dizdar.ui.theme.DizdarRed
import org.tamodak.dizdar.ui.theme.DizdarRowDivider
import org.tamodak.dizdar.ui.theme.DizdarTextFaint

/**
 * The core screen: check apps to block, uncheck to unblock, press Save.
 *
 * Every app is checkable. Android refuses to suspend some packages, but which ones is internal
 * and varies by version and OEM, so there is no attempt to predict it — Save reports whatever the
 * system rejected and puts those checkboxes back.
 *
 * @param state supplies the inventory, the applied set and the pending selection.
 * @param onToggle ticks or clears one row's checkbox.
 * @param onSave pushes the pending selection to the OS.
 * @param onDiscard throws the pending selection away.
 * @param onBack leaves the screen.
 * @param onDismissFailures closes the dialog listing packages Android refused.
 * @param iconLoader decodes a row's icon on demand, so only visible rows cost anything.
 * @param modifier applied to the screen.
 */
@Composable
fun AppListScreen(
    state: DizdarUiState,
    onToggle: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    onDismissFailures: () -> Unit,
    iconLoader: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAllPackages by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    /** Unsaved ticks are easy to lose by reflex, so back asks before discarding them. */
    fun attemptBack() {
        if (state.hasPendingChanges) confirmDiscard = true else onBack()
    }

    BackHandler { attemptBack() }

    // Keyed on everything the filter reads, so a recomposition caused by an unrelated state change
    // (busy, a toast, a checkbox) reuses the previous list instead of re-filtering hundreds of
    // entries. Deliberately excludes `selection`, which changes on every tick.
    val visibleApps = remember(state.apps, query, selectedTab, showAllPackages) {
        state.apps.filter { app ->
            val matchesTab = app.isSystem == (selectedTab == 1)
            val matchesVisibility = showAllPackages || app.isLaunchable
            val matchesQuery = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matchesTab && matchesVisibility && matchesQuery
        }
    }

    DizdarScreen(
        title = stringResource(R.string.apps_title),
        modifier = modifier,
        onBack = ::attemptBack,
        scrollable = false,
        bottomBar = {
            if (state.hasPendingChanges) {
                SaveBar(
                    pending = state.pendingChanges,
                    enabled = !state.busy && state.isDeviceOwner,
                    onSave = onSave,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = DizdarGutter, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.isDeviceOwner) {
                DizdarPanel(borderColor = DizdarRed) {
                    DizdarBody(stringResource(R.string.apps_needs_owner))
                }
            }

            DizdarField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.apps_search),
                leadingIcon = DizdarIcons.Search,
            )

            DizdarTabs(
                titles = listOf(
                    stringResource(R.string.apps_tab_user),
                    stringResource(R.string.apps_tab_system),
                ),
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )

            DizdarToggleRow(
                title = stringResource(R.string.apps_show_all),
                checked = showAllPackages,
                onCheckedChange = { showAllPackages = it },
            )
        }

        DizdarRule()

        when {
            state.appsLoading -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = DizdarGreen) }

            visibleApps.isEmpty() -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                DizdarBody(
                    text = stringResource(R.string.apps_empty),
                    modifier = Modifier.padding(DizdarGutter),
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = DizdarGutter, vertical = 8.dp),
            ) {
                items(visibleApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        checked = app.packageName in state.selection,
                        enabled = state.isDeviceOwner && !state.busy,
                        onCheckedChange = { onToggle(app.packageName, it) },
                        iconLoader = iconLoader,
                    )
                }
            }
        }
    }

    if (state.saveFailures.isNotEmpty()) {
        DizdarDialog(
            title = stringResource(R.string.apps_failed_title),
            body = stringResource(R.string.apps_failed_body),
            accent = DizdarRed,
            onDismiss = onDismissFailures,
            dismissText = stringResource(R.string.ok),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.saveFailures.forEach { packageName ->
                    DizdarBody(
                        text = "• ${state.labelFor(packageName)}",
                        color = DizdarForeground,
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        DizdarDialog(
            title = stringResource(R.string.apps_discard_title),
            body = stringResource(R.string.apps_discard_body),
            accent = DizdarRed,
            onDismiss = { confirmDiscard = false },
            confirmText = stringResource(R.string.discard),
            onConfirm = {
                confirmDiscard = false
                onDiscard()
                onBack()
            },
            dismissText = stringResource(R.string.cancel),
        )
    }
}

/**
 * The pinned bar that appears once the picker holds unsaved changes.
 *
 * Shows the count rather than just a button, so the user can tell at a glance whether the pending
 * set is what they meant before committing it.
 *
 * @param pending how many checkboxes differ from what is applied.
 * @param enabled false while a save is already in flight.
 * @param onSave pushes the selection to the OS.
 */
@Composable
private fun SaveBar(pending: Int, enabled: Boolean, onSave: () -> Unit) {
    Column {
        DizdarRule(color = DizdarDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DizdarBackground)
                .padding(horizontal = DizdarGutter, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DizdarBody(
                text = stringResource(R.string.apps_save_pending, pending),
                color = DizdarForeground,
                modifier = Modifier.weight(1f),
            )
            DizdarPrimaryButton(
                text = stringResource(R.string.save),
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One package in the list: icon, label, package name, checkbox.
 *
 * The whole row is the touch target and the checkbox is inert, so a mis-tap anywhere on the row
 * still toggles it.
 *
 * @param app the package this row represents.
 * @param checked whether it is selected for blocking.
 * @param enabled false while a save is in flight.
 * @param onCheckedChange called with the new state when the row is tapped.
 * @param iconLoader decodes this row's icon. Passed as a loader rather than a decoded bitmap so
 *   only rows that reach the screen cost anything.
 */
@Composable
private fun AppRow(
    app: AppEntry,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconLoader: suspend (String) -> ImageBitmap?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(packageName = app.packageName, iconLoader = iconLoader)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DizdarForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = DizdarTextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            DizdarCheckbox(checked = checked, enabled = enabled)
        }
        DizdarRule(color = DizdarRowDivider)
    }
}

/**
 * Loads one icon lazily, per row.
 *
 * [produceState] keyed on the package name starts the load when the row scrolls into view and
 * cancels it when the row leaves — so scrolling fast never queues hundreds of decodes. The slot is
 * reserved up front so rows do not reflow as icons arrive.
 *
 * `contentDescription = null` is correct here: the label and package name sit right next to the
 * icon, so announcing it too would just repeat them.
 */
@Composable
private fun AppIcon(packageName: String, iconLoader: suspend (String) -> ImageBitmap?) {
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = iconLoader(packageName)
    }
    val bitmap = icon
    if (bitmap == null) {
        DizdarIconBox(icon = DizdarIcons.Package)
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(2.dp, DizdarRowDivider, RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(30.dp))
        }
    }
}

/** Resolves a package name to "Label (com.pkg)", falling back to the bare name if it is unknown. */
private fun DizdarUiState.labelFor(packageName: String): String =
    apps.firstOrNull { it.packageName == packageName }?.let { "${it.label} ($packageName)" }
        ?: packageName
