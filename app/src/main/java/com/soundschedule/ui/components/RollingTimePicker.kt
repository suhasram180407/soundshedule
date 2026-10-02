package com.soundschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundschedule.theme.DarkCardBorder
import com.soundschedule.theme.DarkSurfaceElevated
import com.soundschedule.theme.PrimaryAccent
import com.soundschedule.theme.TextMuted
import com.soundschedule.theme.TextPrimary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val ITEM_HEIGHT: Dp = 44.dp
private val WHEEL_VISIBLE_COUNT = 3
private val TOTAL_WHEEL_HEIGHT: Dp = ITEM_HEIGHT * WHEEL_VISIBLE_COUNT // 132.dp

/**
 * Vertical rolling/wheel-style time picker.
 * Hours (01-12), Minutes (00-59), AM/PM.
 * Selected value is centered and highlighted.
 * Values above and below are partially visible and dimmed.
 * Strictly avoids circular/analog clock faces.
 */
@Composable
fun RollingTimePicker(
    initialHour: Int, // 0..23
    initialMinute: Int, // 0..59
    onTimeChanged: (hour24: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialIsAm = initialHour < 12
    val initialHour12 = when (val h = initialHour % 12) {
        0 -> 12
        else -> h
    }

    val hoursList = remember { (1..12).map { it.toString().padStart(2, '0') } }
    val minutesList = remember { (0..59).map { it.toString().padStart(2, '0') } }
    val amPmList = remember { listOf("AM", "PM") }

    var selectedHourIndex by remember { mutableIntStateOf((initialHour12 - 1).coerceIn(0, 11)) }
    var selectedMinuteIndex by remember { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    var selectedAmPmIndex by remember { mutableIntStateOf(if (initialIsAm) 0 else 1) }

    fun notifyChanged(hourIdx: Int, minIdx: Int, amPmIdx: Int) {
        val h12 = hourIdx + 1
        val isAm = amPmIdx == 0
        val h24 = if (isAm) {
            if (h12 == 12) 0 else h12
        } else {
            if (h12 == 12) 12 else h12 + 12
        }
        onTimeChanged(h24, minIdx)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TOTAL_WHEEL_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        // Center highlighted selection bar indicator
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(ITEM_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, PrimaryAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        )

        Row(
            modifier = Modifier.fillMaxWidth(0.88f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hours Wheel Column
            RollingWheelColumn(
                items = hoursList,
                selectedIndex = selectedHourIndex,
                onSelectedIndexChanged = {
                    selectedHourIndex = it
                    notifyChanged(it, selectedMinuteIndex, selectedAmPmIndex)
                },
                modifier = Modifier.weight(1f)
            )

            // Colon separator
            Text(
                text = ":",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryAccent,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Minutes Wheel Column
            RollingWheelColumn(
                items = minutesList,
                selectedIndex = selectedMinuteIndex,
                onSelectedIndexChanged = {
                    selectedMinuteIndex = it
                    notifyChanged(selectedHourIndex, it, selectedAmPmIndex)
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // AM / PM Wheel Column
            RollingWheelColumn(
                items = amPmList,
                selectedIndex = selectedAmPmIndex,
                onSelectedIndexChanged = {
                    selectedAmPmIndex = it
                    notifyChanged(selectedHourIndex, selectedMinuteIndex, it)
                },
                modifier = Modifier.weight(0.9f)
            )
        }
    }
}

@Composable
private fun RollingWheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val coroutineScope = rememberCoroutineScope()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val currentCenterIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex.coerceIn(0, items.size - 1)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx ->
                val clamped = idx.coerceIn(0, items.size - 1)
                onSelectedIndexChanged(clamped)
            }
    }

    // Scroll to position if external change occurs
    LaunchedEffect(selectedIndex) {
        if (listState.firstVisibleItemIndex != selectedIndex && !listState.isScrollInProgress) {
            listState.scrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier.height(TOTAL_WHEEL_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.height(TOTAL_WHEEL_HEIGHT),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top padding slot so index 0 can align in the middle slot
            item {
                Spacer(modifier = Modifier.height(ITEM_HEIGHT))
            }

            items(items.size) { index ->
                val isSelected = index == currentCenterIndex
                val isNeighbor = Math.abs(index - currentCenterIndex) == 1

                val textColor = when {
                    isSelected -> PrimaryAccent
                    isNeighbor -> TextPrimary.copy(alpha = 0.45f)
                    else -> TextMuted.copy(alpha = 0.2f)
                }

                val fontSize = when {
                    isSelected -> 24.sp
                    isNeighbor -> 17.sp
                    else -> 14.sp
                }

                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT)
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom padding slot so the last index can align in the middle slot
            item {
                Spacer(modifier = Modifier.height(ITEM_HEIGHT))
            }
        }
    }
}
