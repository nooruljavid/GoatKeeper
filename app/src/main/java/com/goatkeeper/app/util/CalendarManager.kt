package com.goatkeeper.app.util

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.goatkeeper.app.data.FarmRecord
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class CalendarManager(private val context: Context) {

    /** Adds a reminder to the user's primary Google Calendar */
    fun addReminder(record: FarmRecord, goatName: String) {
        if (record.dueDate.isBlank()) return

        try {
            val date = LocalDate.parse(record.dueDate)
            val startMillis: Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis: Long = startMillis + (60 * 60 * 1000) // 1 hour duration

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "GoatKeeper: ${record.type} - $goatName")
                put(CalendarContract.Events.DESCRIPTION, "Reminder for ${record.title}. ${record.details}")
                put(CalendarContract.Events.CALENDAR_ID, 1) // Default primary calendar
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}