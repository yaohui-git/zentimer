package com.stilltimer.personal

import android.content.Context

/** 单一 SharedPreferences 入口：只存设置与计时锚点，不存任何内容/账号数据。 */
internal fun Context.prefs() = getSharedPreferences("stilltimer", Context.MODE_PRIVATE)
