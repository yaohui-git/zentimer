package com.yaohui.zentimer

/**
 * 音色与配图为随包内置的实物录音与图片素材（第三方资产，不在代码开源授权范围内）：
 * - 9 段 bell_*.mp3 实物录音 + silent.mp3 静音轨
 * - 13 段环境音（b1 自然系列 / dbs 器乐系列 / 雨滴 / 海浪 / Deep Om）
 * - 每段对应的颂钵实物照片或封面图，取自 assets/timer/images/
 * 原样提取，音频未重采样、未重合成；图片仅等比缩放。名称按当前语言取简 / 繁 / 英三套。
 */
data class Sound(
    val id: String,
    val res: Int,
    val icon: Int,
    val hans: String,
    val hant: String,
    val en: String
) {
    val name: String
        get() = when (Lang.current) {
            Lang.Mode.HANT -> hant
            Lang.Mode.EN -> en
            else -> hans
        }
}

object SoundCatalog {

    val bells = listOf(
        Sound("b_basu",    R.raw.b_basu,    R.drawable.ic_b_basu,    "颂钵 · Basu",    "頌缽 · Basu",    "Singing Bowl · Basu"),
        Sound("b_dengze",  R.raw.b_dengze,  R.drawable.ic_b_dengze,  "颂钵 · Dengze",  "頌缽 · Dengze",  "Singing Bowl · Dengze"),
        Sound("b_kangse",  R.raw.b_kangse,  R.drawable.ic_b_kangse,  "颂钵 · Kangse",  "頌缽 · Kangse",  "Singing Bowl · Kangse"),
        Sound("b_ombu",    R.raw.b_ombu,    R.drawable.ic_b_ombu,    "颂钵 · Ombu",    "頌缽 · Ombu",    "Singing Bowl · Ombu"),
        Sound("b_sakya",   R.raw.b_sakya,   R.drawable.ic_b_sakya,   "颂钵 · Sakya",   "頌缽 · Sakya",   "Singing Bowl · Sakya"),
        Sound("b_shurong", R.raw.b_shurong, R.drawable.ic_b_shurong, "颂钵 · Shurong", "頌缽 · Shurong", "Singing Bowl · Shurong"),
        Sound("b_zhada",   R.raw.b_zhada,   R.drawable.ic_b_zhada,   "颂钵 · Zhada",   "頌缽 · Zhada",   "Singing Bowl · Zhada"),
        Sound("b_gong",    R.raw.b_gong,    R.drawable.ic_b_gong,    "寺院大锣",        "寺院大鑼",        "Temple Gong"),
        Sound("b_wood",    R.raw.b_wood,    R.drawable.ic_b_wood,    "木鱼",            "木魚",            "Wood Block"),
        Sound("b_silent",  R.raw.b_silent,  R.drawable.ic_no_music,  "静音",            "靜音",            "Silent")
    )

    val ambiences = listOf(
        Sound("none",            0,                      R.drawable.ic_amb_none,        "无",         "無",         "None"),
        Sound("amb_rain",        R.raw.amb_rain,        R.drawable.ic_amb_rain,        "雨滴",       "雨滴",       "Rain Drops"),
        Sound("amb_ocean",       R.raw.amb_ocean,       R.drawable.ic_amb_ocean,       "海浪",       "海浪",       "Ocean Waves"),
        Sound("amb_om",          R.raw.amb_om,          R.drawable.ic_amb_om,          "深层嗡鸣",   "深層嗡鳴",   "Deep Om"),
        Sound("amb_birdsong",    R.raw.amb_birdsong,    R.drawable.ic_amb_birdsong,    "鸟鸣",       "鳥鳴",       "Birdsong"),
        Sound("amb_celtic",      R.raw.amb_celtic,      R.drawable.ic_amb_celtic,      "凯尔特和声", "凱爾特和聲", "Celtic Harmony"),
        Sound("amb_natures",     R.raw.amb_natures,     R.drawable.ic_amb_natures,     "自然旋律",   "自然旋律",   "Nature's Melody"),
        Sound("amb_reflections", R.raw.amb_reflections, R.drawable.ic_amb_reflections, "静思",       "靜思",       "Reflections"),
        Sound("amb_stillness",   R.raw.amb_stillness,   R.drawable.ic_amb_stillness,   "寂静",       "寂靜",       "Stillness"),
        Sound("amb_tibetan",     R.raw.amb_tibetan,     R.drawable.ic_amb_tibetan,     "藏地晨光",   "藏地晨光",   "Tibetan Sunrise"),
        Sound("amb_piano",       R.raw.amb_piano,       R.drawable.ic_amb_piano,       "月光钢琴",   "月光鋼琴",   "Moonlight Piano"),
        Sound("amb_guitar",      R.raw.amb_guitar,      R.drawable.ic_amb_guitar,      "禅意吉他",   "禪意吉他",   "Zen Guitar"),
        Sound("amb_stream",      R.raw.amb_stream,      R.drawable.ic_amb_stream,      "永恒溪流",   "永恆溪流",   "Eternal Stream")
    )

    fun bell(id: String): Sound = bells.firstOrNull { it.id == id } ?: bells[0]
    fun ambience(id: String): Sound = ambiences.firstOrNull { it.id == id } ?: ambiences[0]
}
