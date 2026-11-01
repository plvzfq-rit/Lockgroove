package io.github.plvzfqrit.lockgroove.lyrics

/**
 * Words the dictionary reads wrongly, put right. Japanese counts people and days with old native
 * readings (二人 is "futari", not "ni nin"), and the general dictionary splits those words apart.
 * Add a line here when a reading is wrong: the word and how it is said, in katakana.
 */
internal object IrregularReadings {
    private val words = listOf(
        "一人" to "ヒトリ",
        "二人" to "フタリ",
        "1人" to "ヒトリ",
        "2人" to "フタリ",
        "二十歳" to "ハタチ",
        "二日" to "フツカ",
        "三日" to "ミッカ",
        "四日" to "ヨッカ",
        "五日" to "イツカ",
        "六日" to "ムイカ",
        "七日" to "ナノカ",
        "八日" to "ヨウカ",
        "九日" to "ココノカ",
        "十日" to "トオカ",
        "二十日" to "ハツカ",
    )

    /** In Kuromoji's user-dictionary format: the word, how to split it (not at all), its reading, a label. */
    val dictionary: String = words.joinToString("\n") { (word, reading) -> "$word,$word,$reading,カスタム名詞" }
}
