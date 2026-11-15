package notifyrelay.desktop.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 独立行之间的纵向间距。 */
internal val OVERLAY_ROW_GAP = 8.dp

/** 同一行内相邻条目之间的横向间距。 */
internal val OVERLAY_SIDE_GAP = 12.dp

/**
 * 叠加层列的一个条目。
 *
 * [key] 用于子组合槽位标识，须在整个列内唯一；[content] 允许不发出任何节点
 * （例如关闭的元素、无内容的提示），此时该条目按零尺寸处理，不占行也不占位。
 */
internal class OverlayColumnItem(
    val key: Any,
    val content: @Composable () -> Unit,
)

/**
 * 叠加层内容列。
 *
 * 每个 [anchors] 元素独占一行并水平居中；[fillers] 按顺序尝试填进各独立行居中后
 * 左右剩下的空档——左空档内的条目整体贴向居中元素右侧，右空档内的条目整体贴向居中元素左侧，
 * 都不贴列边。填不下的条目留到后续独立行继续尝试，所有独立行处理完后仍未安置的条目
 * 按自身宽度折成若干行、每行居中排在最下方。
 *
 * 空档是否够用按实测宽度判定，因此窄胶囊与短文本能真正并排，而宽卡片不会把条目挤偏。
 *
 * @param anchors 独立行内容，按显示顺序排列
 * @param fillers 流式条目，按尝试顺序排列
 */
@Composable
internal fun OverlayColumn(
    anchors: List<OverlayColumnItem>,
    fillers: List<OverlayColumnItem>,
    modifier: Modifier = Modifier,
    rowGap: Dp = OVERLAY_ROW_GAP,
    sideGap: Dp = OVERLAY_SIDE_GAP,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val rowGapPx = rowGap.roundToPx()
        val sideGapPx = sideGap.roundToPx()
        // 内容列按不限高测量：窗口高度由内容反推，不受窗口当前高度约束
        val loose = Constraints(0, width, 0, Constraints.Infinity)

        // 内容可以不发出任何节点，此时子组合结果为空，按零尺寸条目跳过
        val anchorPlaceables =
            anchors.mapNotNull { item ->
                subcompose(SlotKey(isAnchor = true, key = item.key), item.content)
                    .firstOrNull()
                    ?.measure(loose)
                    ?.takeIf { it.width > 0 && it.height > 0 }
            }
        val fillerPlaceables =
            fillers.mapNotNull { item ->
                subcompose(SlotKey(isAnchor = false, key = item.key), item.content)
                    .firstOrNull()
                    ?.measure(loose)
                    ?.takeIf { it.width > 0 && it.height > 0 }
            }

        val rows = mutableListOf<RowPlan>()
        val placed = BooleanArray(fillerPlaceables.size)
        var next = 0

        anchorPlaceables.forEach { anchor ->
            val anchorX = (width - anchor.width) / 2
            val leftSpace = anchorX - sideGapPx
            val rightSpace = width - (anchorX + anchor.width) - sideGapPx

            val left = mutableListOf<Placeable>()
            var leftWidth = 0
            while (next < fillerPlaceables.size) {
                val item = fillerPlaceables[next]
                val needed = if (left.isEmpty()) item.width else leftWidth + sideGapPx + item.width
                if (needed > leftSpace) break
                left.add(item)
                leftWidth = needed
                placed[next] = true
                next++
            }

            val right = mutableListOf<Placeable>()
            var rightWidth = 0
            while (next < fillerPlaceables.size) {
                val item = fillerPlaceables[next]
                val needed = if (right.isEmpty()) item.width else rightWidth + sideGapPx + item.width
                if (needed > rightSpace) break
                right.add(item)
                rightWidth = needed
                placed[next] = true
                next++
            }

            rows.add(RowPlan(anchor = anchor, anchorX = anchorX, left = left, right = right))
        }

        // 剩余条目：按自身宽度折行，每行居中
        val leftover = fillerPlaceables.filterIndexed { index, _ -> !placed[index] }
        val flowRows = mutableListOf<List<Placeable>>()
        var cursor = 0
        while (cursor < leftover.size) {
            val row = mutableListOf<Placeable>()
            var used = 0
            while (cursor < leftover.size) {
                val item = leftover[cursor]
                val needed = if (row.isEmpty()) item.width else used + sideGapPx + item.width
                if (needed > width && row.isNotEmpty()) break
                row.add(item)
                used = needed
                cursor++
            }
            flowRows.add(row)
        }

        val totalRows = rows.size + flowRows.size
        var totalHeight = 0
        rows.forEach { totalHeight += it.height() }
        flowRows.forEach { row ->
            totalHeight += row.maxOfOrNull { it.height } ?: 0
        }
        if (totalRows > 1) totalHeight += rowGapPx * (totalRows - 1)

        layout(width, totalHeight) {
            var y = 0
            rows.forEach { plan ->
                val rowHeight = plan.height()
                val anchorY = y + (rowHeight - plan.anchor.height) / 2
                plan.anchor.place(plan.anchorX, anchorY)

                // 左空档：整组贴向居中元素右侧
                var x = plan.anchorX - sideGapPx
                plan.left.asReversed().forEach { item ->
                    x -= item.width
                    item.place(x, y + (rowHeight - item.height) / 2)
                    x -= sideGapPx
                }

                // 右空档：整组贴向居中元素左侧
                var rightX = plan.anchorX + plan.anchor.width + sideGapPx
                plan.right.forEach { item ->
                    item.place(rightX, y + (rowHeight - item.height) / 2)
                    rightX += item.width + sideGapPx
                }

                y += rowHeight + rowGapPx
            }

            flowRows.forEach { row ->
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                val rowWidth = row.sumOf { it.width } + sideGapPx * (row.size - 1).coerceAtLeast(0)
                var x = (width - rowWidth) / 2
                row.forEach { item ->
                    item.place(x, y + (rowHeight - item.height) / 2)
                    x += item.width + sideGapPx
                }
                y += rowHeight + rowGapPx
            }
        }
    }
}

/** 一条独立行：居中元素与填进左右空档的条目。 */
private class RowPlan(
    val anchor: Placeable,
    val anchorX: Int,
    val left: List<Placeable>,
    val right: List<Placeable>,
) {
    /** 行高取居中元素与两侧条目中的最大高度。 */
    fun height(): Int {
        val leftHeight = left.maxOfOrNull { it.height } ?: 0
        val rightHeight = right.maxOfOrNull { it.height } ?: 0
        return maxOf(anchor.height, leftHeight, rightHeight)
    }
}

/** 子组合槽位标识：独立行与流式条目分开编号，避免同键互相覆盖。 */
private data class SlotKey(
    val isAnchor: Boolean,
    val key: Any,
)
