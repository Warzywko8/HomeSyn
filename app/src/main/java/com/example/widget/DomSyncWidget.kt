package com.example.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.data.firebase.ChoreRepository
import com.example.data.widget.PinnedChoreSnapshot
import com.example.data.widget.WidgetSnapshot
import com.example.data.widget.WidgetSnapshotStore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class DomSyncWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetSnapshotStore(context.applicationContext)
        val initialSnapshot = store.getSnapshot()

        provideContent {
            val snapshot by store.observeSnapshot().collectAsState(initial = initialSnapshot)
            GlanceTheme {
                DomSyncWidgetContent(snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun DomSyncWidgetContent(snapshot: WidgetSnapshot) {
    val creamBg = ColorProvider(Color(0xFFFAF7F2))
    val linenCard = ColorProvider(Color(0xFFF2ECE2))
    val sageBg = ColorProvider(Color(0xFFD3E4D7))
    val sageDark = ColorProvider(Color(0xFF12281A))
    val sageAccent = ColorProvider(Color(0xFF4E6E58))
    val terracottaBg = ColorProvider(Color(0xFFF9DFD6))
    val terracottaText = ColorProvider(Color(0xFF8E391E))
    val inkText = ColorProvider(Color(0xFF1E2420))
    val mutedText = ColorProvider(Color(0xFF5A635D))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(creamBg)
            .cornerRadius(20.dp)
            .padding(12.dp)
    ) {
        // Header row: Household name + shopping count pill
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = snapshot.householdName.ifBlank { "HouseSync" },
                    style = TextStyle(
                        color = inkText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = "Centrum domu",
                    style = TextStyle(
                        color = mutedText,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }

            Box(
                modifier = GlanceModifier
                    .background(if (snapshot.remainingShoppingCount > 0) terracottaBg else sageBg)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                val label = when (snapshot.remainingShoppingCount) {
                    0 -> "Zakupy: 0 ✓"
                    1 -> "1 produkt"
                    in 2..4 -> "${snapshot.remainingShoppingCount} produkty"
                    else -> "${snapshot.remainingShoppingCount} produktów"
                }
                Text(
                    text = label,
                    style = TextStyle(
                        color = if (snapshot.remainingShoppingCount > 0) terracottaText else sageDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        if (snapshot.pinnedChores.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(linenCard)
                    .cornerRadius(14.dp)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Otwórz HouseSync i przypnij 2 obowiązki do pulpitu",
                    style = TextStyle(color = mutedText, fontSize = 12.sp),
                    maxLines = 2
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                snapshot.pinnedChores.take(2).forEachIndexed { index, chore ->
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                    WidgetChoreRow(
                        chore = chore,
                        sageBg = sageBg,
                        sageDark = sageDark,
                        linenCard = linenCard,
                        sageAccent = sageAccent,
                        inkText = inkText,
                        mutedText = mutedText
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetChoreRow(
    chore: PinnedChoreSnapshot,
    sageBg: ColorProvider,
    sageDark: ColorProvider,
    linenCard: ColorProvider,
    sageAccent: ColorProvider,
    inkText: ColorProvider,
    mutedText: ColorProvider
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(if (chore.isDone) sageBg else linenCard)
            .cornerRadius(14.dp)
            .clickable(
                actionRunCallback<TogglePinnedChoreAction>(
                    actionParametersOf(
                        TogglePinnedChoreAction.KEY_CHORE_ID to chore.id,
                        TogglePinnedChoreAction.KEY_MARK_DONE to !chore.isDone
                    )
                )
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (chore.isDone) "✓" else "○",
            style = TextStyle(
                color = if (chore.isDone) sageAccent else mutedText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = chore.title,
                style = TextStyle(
                    color = if (chore.isDone) sageDark else inkText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Text(
                text = chore.statusText,
                style = TextStyle(
                    color = if (chore.isDone) sageAccent else mutedText,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

class TogglePinnedChoreAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val choreId = parameters[KEY_CHORE_ID] ?: return
        val markDone = parameters[KEY_MARK_DONE] ?: true
        val appContext = context.applicationContext
        val store = WidgetSnapshotStore(appContext)
        val snapshot = store.getSnapshot()

        // Optimistically update local widget cache immediately
        val updatedChores = snapshot.pinnedChores.map { item ->
            if (item.id == choreId) {
                item.copy(
                    isDone = markDone,
                    statusText = if (markDone) {
                        "Zrobione teraz przez ${snapshot.currentUserDisplayName}"
                    } else {
                        "Do zrobienia"
                    }
                )
            } else {
                item
            }
        }
        store.saveSnapshot(snapshot.copy(pinnedChores = updatedChores))
        DomSyncWidget().update(appContext, glanceId)

        // Sync to Firestore if signed in and householdId is present
        val user = Firebase.auth.currentUser
        if (user != null && snapshot.householdId.isNotBlank()) {
            runCatching {
                val repo = ChoreRepository(appContext)
                repo.toggleCompletion(
                    householdId = snapshot.householdId,
                    choreId = choreId,
                    completedByName = user.displayName ?: snapshot.currentUserDisplayName,
                    markDone = markDone
                )
            }.onFailure { err ->
                Log.w("DomSyncWidget", "Failed syncing chore toggle from widget", err)
            }
        }
    }

    companion object {
        val KEY_CHORE_ID = ActionParameters.Key<String>("chore_id")
        val KEY_MARK_DONE = ActionParameters.Key<Boolean>("mark_done")
    }
}

class DomSyncWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DomSyncWidget()
}
