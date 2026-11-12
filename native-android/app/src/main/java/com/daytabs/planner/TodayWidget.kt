package com.daytabs.planner

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDate

// The existing component name keeps previously pinned widgets attached after an upgrade.
class DaytabsMotivationProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = inBackground {
        refresh(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            refresh(context, manager, manager.getAppWidgetIds(ComponentName(context, DaytabsMotivationProvider::class.java)))
        }

        @Suppress("DEPRECATION")
        private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val today = LocalDate.now()
            val entries = PlannerStore(context).load().todos(today)
            val openTodos = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(REMINDER_TAB, Tab.TODOS.name)
            val tap = PendingIntent.getActivity(context, 400, openTodos,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            for (id in ids) {
                val adapter = Intent(context, TodayWidgetService::class.java)
                    .setData(Uri.parse("kaizen://widget/$id"))
                val widget = RemoteViews(context.packageName, R.layout.today_widget).apply {
                    setTextViewText(R.id.widget_status, "${dayLabel(today)} · ${entries.count { it.completed }} of ${entries.size} done")
                    setRemoteAdapter(R.id.widget_todos, adapter)
                    setEmptyView(R.id.widget_todos, R.id.widget_empty)
                    setOnClickPendingIntent(R.id.widget_heading, tap)
                    setOnClickPendingIntent(R.id.widget_empty, tap)
                }
                manager.updateAppWidget(id, widget)
            }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_todos)
        }
    }
}

class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TodayTodoRows(applicationContext)
}

private class TodayTodoRows(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var entries = emptyList<Entry>()

    override fun onCreate() = Unit
    override fun onDataSetChanged() {
        entries = PlannerStore(context).load().todos(LocalDate.now())
    }

    override fun onDestroy() {
        entries = emptyList()
    }

    override fun getCount() = entries.size
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = entries[position].id.hashCode().toLong()
    override fun hasStableIds() = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews? {
        val entry = entries.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.today_widget_row).apply {
            setTextViewText(R.id.widget_task_title, entry.title)
            setTextViewText(R.id.widget_task_state, context.getString(
                if (entry.completed) R.string.widget_task_done else R.string.widget_task_open,
            ))
        }
    }
}
