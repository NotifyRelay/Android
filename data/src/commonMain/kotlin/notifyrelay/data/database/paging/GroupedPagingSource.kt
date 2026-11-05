package notifyrelay.data.database.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * 按分组的偏移分页源基类。
 *
 * 通知历史与超级岛历史的分页骨架相同：先取稳定的分组数并缓存，再按 `offset` 切片，逐组取回条目
 * 并包装成带 `latestTime` 的分组视图。差异只有「总结分组数」与「取一页条目」两点，由子类实现。
 *
 * 子类必须保证分组顺序稳定：分页键是分组下标的偏移，顺序变化会让已加载的页错位。
 *
 * @param T 页面元素类型。
 */
abstract class GroupedPagingSource<T : Any> : PagingSource<Int, T>() {
    /** 缓存的分组数；首次加载时取得，避免每次翻页重算。 */
    private var groupCount: Int? = null

    /**
     * 取分组总数。
     *
     * @returns 分组数；顺序必须稳定。
     */
    protected abstract suspend fun loadGroupCount(): Int

    /**
     * 取一页分组对应的页面元素。
     *
     * @param offset 起始分组下标。
     * @param limit 本页最多取多少个分组。
     * @returns 本页元素。
     */
    protected abstract suspend fun loadPage(
        offset: Int,
        limit: Int,
    ): List<T>

    /**
     * 加载失败时构造的错误结果；调用方按自身可恢复的失败类型收窄。
     *
     * @param cause 捕获到的异常。
     * @returns 错误结果。
     */
    protected abstract fun onLoadError(cause: Exception): LoadResult.Error<Int, T>

    final override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        return try {
            val offset = params.key ?: 0
            val total = groupCount ?: loadGroupCount().also { groupCount = it }
            if (total == 0) return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
            val data = loadPage(offset, params.loadSize)
            val pageGroups = data.size.coerceAtMost(total - offset)
            val nextKey = if (offset + pageGroups >= total) null else offset + pageGroups
            val prevKey = if (offset == 0) null else maxOf(offset - params.loadSize, 0)
            LoadResult.Page(data = data, prevKey = prevKey, nextKey = nextKey)
        } catch (e: Exception) {
            onLoadError(e)
        }
    }

    final override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition) ?: return null
        return page.prevKey?.let { it + state.config.pageSize } ?: page.nextKey?.let { it - state.config.pageSize }
    }
}
