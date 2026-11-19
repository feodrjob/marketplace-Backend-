package catalog.domain.model

object TextValues {
    fun trimToNull(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }
}