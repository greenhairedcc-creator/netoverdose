package app.netoverdose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.netoverdose.R
import app.netoverdose.design.LocalDesign
import app.netoverdose.domain.DeadlineOptions
import app.netoverdose.domain.TimeText
import app.netoverdose.ui.theme.MonoFamily as Mono
import java.time.ZoneId

/**
 * Inline calendar for the "Своя" deadline. Selecting a day converts the picker's UTC date
 * to the local end of that day, matching the other deadline presets.
 *
 * Shared by the intake panel and the task card editor so both pickers behave identically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomDateBlock(selected: Long?, zone: ZoneId, onSelect: (Long) -> Unit) {
    val palette = LocalDesign.current
    val today = remember {
        java.time.Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
    }
    val initial = remember(selected) {
        selected
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: today.plusDays(1)
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = DeadlineOptions.toUtcDate(initial)
    )

    // Mirror the picker selection into the form so Save can enable itself.
    LaunchedEffect(pickerState.selectedDateMillis) {
        val picked = pickerState.selectedDateMillis ?: return@LaunchedEffect
        onSelect(DeadlineOptions.fromUtcDate(picked, zone))
    }

    Column(
        Modifier.fillMaxWidth().clip(palette.shape).border(1.dp, palette.rule, palette.shape)
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DatePicker(
            state = pickerState,
            showModeToggle = false,
            title = {
                Text(
                    stringResource(R.string.deadline_pick_day),
                    style = TextStyle(fontFamily = Mono, fontSize = 11.sp),
                    color = palette.muted,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                )
            },
            headline = null,
            modifier = Modifier.fillMaxWidth(),
            colors = DatePickerDefaults.colors(
                containerColor = palette.bg,
                titleContentColor = palette.muted,
                headlineContentColor = palette.ink,
                weekdayContentColor = palette.muted,
                subheadContentColor = palette.muted,
                navigationContentColor = palette.ink,
                yearContentColor = palette.text,
                currentYearContentColor = palette.accentText,
                selectedYearContentColor = palette.onAccent,
                selectedYearContainerColor = palette.accent,
                dayContentColor = palette.text,
                selectedDayContentColor = palette.onAccent,
                selectedDayContainerColor = palette.accent,
                todayContentColor = palette.accentText,
                todayDateBorderColor = palette.accentText
            )
        )
        selected?.let {
            Text(
                stringResource(
                    R.string.deadline_chosen,
                    TimeText.dayLabel(it, zone),
                    TimeText.dateShort(it, zone)
                ),
                style = TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = palette.accentText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

/** 1..5 selector used both by the intake panel and the card editor. */
@Composable
internal fun DifficultyPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalDesign.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..5).forEach { level ->
            val active = level - 1 == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(palette.shape)
                    .background(if (active) palette.ink else Color.Transparent)
                    .border(1.dp, if (active) palette.ink else palette.rule, palette.shape)
                    .clickable { onSelect(level - 1) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    level.toString(),
                    style = TextStyle(
                        fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    ),
                    color = if (active) palette.bg else palette.muted
                )
            }
        }
    }
}

internal fun difficultyLabel(index: Int): Int = when (index) {
    0 -> R.string.diff_1
    1 -> R.string.diff_2
    2 -> R.string.diff_3
    3 -> R.string.diff_4
    else -> R.string.diff_5
}
