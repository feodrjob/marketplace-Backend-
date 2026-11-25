package catalog.domain.model

/**
 * Оценка совпадения текста витрины с запросом гостя.
 *
 * <p>
 * Ищет по названию и описанию. Лексическое вхождение весит больше,
 * чем похожесть триграмм. Слово в названии весит больше того же слова в описании.
 * Отдельная модель и внешний поисковый движок не используются.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see ProductListingQuery
 */
object CatalogTextScore {
    const val TRIGRAM_THRESHOLD = 0.3
    private const val TITLE_LEXICAL = 2.0
    private const val DESCRIPTION_LEXICAL = 1.0
    private const val TITLE_TRIGRAM = 1.0
    private const val DESCRIPTION_TRIGRAM = 0.5
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{Nd}]+")

    /**
     * Считает вес совпадения.
     *
     * @param title название товара
     * @param description описание или `null`
     * @param query текст гостя после обрезки пробелов
     * @return вес больше нуля, если каждое слово запроса нашлось, иначе `0`
     */
    fun score(title: String, description: String?, query: String): Double {
        val tokens = tokens(query)
        if (tokens.isEmpty()) {
            return 0.0
        }
        val titleText = normalize(title)
        val descriptionText = normalize(description)
        var total = 0.0
        for (token in tokens) {
            val tokenScore = fieldScore(titleText, token, TITLE_LEXICAL, TITLE_TRIGRAM) +
                fieldScore(descriptionText, token, DESCRIPTION_LEXICAL, DESCRIPTION_TRIGRAM)
            if (tokenScore == 0.0) {
                return 0.0
            }
            total += tokenScore
        }
        return total
    }

    /**
     * Делит текст на слова поиска.
     *
     * @param value исходная строка
     * @return слова в нижнем регистре
     */
    fun tokens(value: String): List<String> {
        return normalize(value).split(' ').filter { token -> token.isNotEmpty() }
    }

    private fun fieldScore(text: String, token: String, lexicalWeight: Double, trigramWeight: Double): Double {
        if (text.isEmpty()) {
            return 0.0
        }
        if (text.contains(token)) {
            return lexicalWeight
        }
        val similarity = bestSimilarity(token, text)
        if (similarity < TRIGRAM_THRESHOLD) {
            return 0.0
        }
        return trigramWeight * similarity
    }

    private fun bestSimilarity(token: String, text: String): Double {
        var best = similarity(token, text)
        for (word in text.split(' ')) {
            if (word.isNotEmpty()) {
                best = maxOf(best, similarity(token, word))
            }
        }
        return best
    }

    private fun similarity(left: String, right: String): Double {
        val leftGrams = trigrams(left)
        val rightGrams = trigrams(right)
        if (leftGrams.isEmpty() || rightGrams.isEmpty()) {
            return 0.0
        }
        var shared = 0
        for (gram in leftGrams) {
            if (gram in rightGrams) {
                shared += 1
            }
        }
        return (2.0 * shared) / (leftGrams.size + rightGrams.size)
    }

    @Suppress("KotlinConstantConditions")
    private fun trigrams(value: String): Set<String> {
        val padded = "  $value  "
        if (padded.length < 3) {
            return emptySet()
        }
        val grams = linkedSetOf<String>()
        for (index in 0..padded.length - 3) {
            grams.add(padded.substring(index, index + 3))
        }
        return grams
    }

    private fun normalize(value: String?): String {
        if (value.isNullOrBlank()) {
            return ""
        }
        return value.lowercase().trim().replace(TOKEN_SPLIT, " ").trim()
    }
}
