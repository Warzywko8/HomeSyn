package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.data.model.ChoreResetMode
import com.example.data.model.ChoreTile
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class EvaluatedChoreStatus(
    val isDoneForCurrentPeriod: Boolean,
    val statusTextPl: String,
    val scheduleHintPl: String
)

object ChoreResetEvaluator {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

    fun evaluate(
        chore: ChoreTile,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): EvaluatedChoreStatus {
        val mode = chore.mode()
        val lastTimestamp = chore.lastCompletedAt
        val scheduleHint = formatScheduleHint(mode, chore.resetHours)

        if (lastTimestamp == null) {
            return EvaluatedChoreStatus(
                isDoneForCurrentPeriod = false,
                statusTextPl = "Do zrobienia",
                scheduleHintPl = scheduleHint
            )
        }

        val completedMillis = lastTimestamp.toDate().time
        val latestBoundaryMillis = latestResetBoundaryMillis(mode, chore.resetHours, nowMillis, zoneId)
        val isDone = when (mode) {
            ChoreResetMode.NONE -> true
            ChoreResetMode.DAILY_MIDNIGHT,
            ChoreResetMode.SCHEDULED_HOURS -> completedMillis >= latestBoundaryMillis
        }

        if (!isDone) {
            return EvaluatedChoreStatus(
                isDoneForCurrentPeriod = false,
                statusTextPl = "Do zrobienia",
                scheduleHintPl = scheduleHint
            )
        }

        val completedDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(completedMillis), zoneId)
        val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId).toLocalDate()
        val who = chore.lastCompletedByName?.takeIf { it.isNotBlank() } ?: "Domownika"
        val timeStr = completedDateTime.format(timeFormatter)

        val dayLabel = when (completedDateTime.toLocalDate()) {
            today -> "dziś"
            today.minusDays(1) -> "wczoraj"
            else -> completedDateTime.format(dateFormatter)
        }

        return EvaluatedChoreStatus(
            isDoneForCurrentPeriod = true,
            statusTextPl = "Zrobione $dayLabel o $timeStr przez $who",
            scheduleHintPl = scheduleHint
        )
    }

    fun latestResetBoundaryMillis(
        mode: ChoreResetMode,
        resetHours: List<Long>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val today = nowZdt.toLocalDate()

        return when (mode) {
            ChoreResetMode.NONE -> Long.MIN_VALUE
            ChoreResetMode.DAILY_MIDNIGHT -> {
                today.atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
            ChoreResetMode.SCHEDULED_HOURS -> {
                val hours = resetHours.map { it.toInt().coerceIn(0, 23) }.distinct().sorted()
                if (hours.isEmpty()) {
                    today.atStartOfDay(zoneId).toInstant().toEpochMilli()
                } else {
                    val todayCandidates = hours.map { h ->
                        today.atTime(LocalTime.of(h, 0)).atZone(zoneId).toInstant().toEpochMilli()
                    }.filter { it <= nowMillis }

                    todayCandidates.maxOrNull() ?: run {
                        val yesterday: LocalDate = today.minusDays(1)
                        val maxHour = hours.max()
                        yesterday.atTime(LocalTime.of(maxHour, 0)).atZone(zoneId).toInstant().toEpochMilli()
                    }
                }
            }
        }
    }

    fun formatScheduleHint(mode: ChoreResetMode, resetHours: List<Long>): String {
        return when (mode) {
            ChoreResetMode.NONE -> "Reset ręczny"
            ChoreResetMode.DAILY_MIDNIGHT -> "Reset o 00:00"
            ChoreResetMode.SCHEDULED_HOURS -> {
                val hours = resetHours.map { it.toInt().coerceIn(0, 23) }.distinct().sorted()
                if (hours.isEmpty()) "Reset o 00:00"
                else "Reset: ${hours.joinToString(", ") { String.format("%02d:00", it) }}"
            }
        }
    }
}
