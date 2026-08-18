package com.stalkerapp.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sürükle-bırak (drag-and-drop) destekli LazyColumn.
 * Ekstra kütüphane gerektirmez — Compose pointer API ile uygulanmıştır.
 *
 * Kullanım:
 * ```
 * DragDropList(items = channels, onMove = { from, to -> reorder(from, to) }) { item, isDragging ->
 *     ChannelRow(channel = item, modifier = Modifier.alpha(if (isDragging) 0.5f else 1f))
 * }
 * ```
 */
@Composable
fun <T : Any> DragDropList(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    itemContent: @Composable (item: T, isDragging: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(items) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    // Dokunulan öğeyi bul
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val touchY = offset.y
                    val found = visibleItems.firstOrNull { info ->
                        touchY >= info.offset && touchY < info.offset + info.size
                    }
                    if (found != null) {
                        draggingIndex = found.index
                        dragOffsetY = 0f
                        isDragging = true
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    if (draggingIndex < 0) return@detectDragGesturesAfterLongPress
                    dragOffsetY += dragAmount.y

                    // Hedef indeksi hesapla
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val currentItem = visibleItems.firstOrNull { it.index == draggingIndex }
                        ?: return@detectDragGesturesAfterLongPress
                    val centerY = currentItem.offset + currentItem.size / 2f + dragOffsetY

                    val target = visibleItems.firstOrNull { info ->
                        centerY >= info.offset && centerY < info.offset + info.size
                    }
                    if (target != null && target.index != draggingIndex) {
                        onMove(draggingIndex, target.index)
                        draggingIndex = target.index
                        dragOffsetY = 0f
                    }

                    // Kenar otomatik kaydırma
                    val listTop = layoutInfo.viewportStartOffset.toFloat()
                    val listBottom = layoutInfo.viewportEndOffset.toFloat()
                    val absY = change.position.y
                    autoScrollJob?.cancel()
                    when {
                        absY < listTop + 60f -> {
                            autoScrollJob = scope.launch {
                                while (true) {
                                    listState.animateScrollBy(-10f)
                                    delay(16)
                                }
                            }
                        }
                        absY > listBottom - 60f -> {
                            autoScrollJob = scope.launch {
                                while (true) {
                                    listState.animateScrollBy(10f)
                                    delay(16)
                                }
                            }
                        }
                        else -> autoScrollJob = null
                    }
                },
                onDragEnd = {
                    isDragging = false
                    draggingIndex = -1
                    dragOffsetY = 0f
                    autoScrollJob?.cancel()
                    autoScrollJob = null
                },
                onDragCancel = {
                    isDragging = false
                    draggingIndex = -1
                    dragOffsetY = 0f
                    autoScrollJob?.cancel()
                    autoScrollJob = null
                }
            )
        }
    ) {
        itemsIndexed(items, key = { _, item -> item.hashCode() }) { index, item ->
            val isBeingDragged = isDragging && index == draggingIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset {
                        IntOffset(
                            x = 0,
                            y = if (isBeingDragged) dragOffsetY.roundToInt() else 0
                        )
                    }
            ) {
                itemContent(item, isBeingDragged)
            }
        }
    }
}
