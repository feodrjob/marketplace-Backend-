package catalog.domain.model

/**
 * Обрезка текста перед записью в модель.
 *
 * <p>
 * Пустая строка и строка из пробелов становятся отсутствием значения,
 * чтобы необязательные поля не хранили пустышку.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see CategoryModel
 */
object TextValues {
    /**
     * Убирает пробелы по краям.
     *
     * @param value исходный текст, может отсутствовать
     * @return обрезанный текст или `null`, если после обрезки ничего не осталось
     */
    fun trimToNull(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }
}