package com.xzyht.notifyrelay.feature.appslist.model

/**
 * 判断应用是否匹配搜索关键字。
 *
 * 关键字为空或仅空白字符时视为匹配全部；否则匹配应用名或包名（忽略大小写）。
 *
 * @param appName 应用显示名。
 * @param packageName 应用包名。
 * @param query 搜索关键字。
 * @return 匹配返回 true，否则返回 false。
 */
fun appMatchesQuery(
    appName: String,
    packageName: String,
    query: String,
): Boolean =
    query.isBlank() ||
        appName.contains(query, ignoreCase = true) ||
        packageName.contains(query, ignoreCase = true)
