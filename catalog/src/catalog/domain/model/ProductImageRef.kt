package catalog.domain.model

import java.util.UUID

/**
 * Связь товара с файлом Media.
 *
 * <p>
 * Один файл может быть привязан к разным товарам, поэтому ключ связи —
 * [productId] вместе с [mediaId]. Адрес картинки хранит Media.
 * </p>
 *
 * @param productId товар, к которому привязан файл
 * @param mediaId идентификатор файла в Media
 * @param sortOrder порядок кадра в карточке, меньше — раньше
 * @since 0.0.1
 * @author lllnightlll
 * @see ProductModel
 */
class ProductImageRef(
    val productId: UUID,
    val mediaId: UUID,
    val sortOrder: Int,
)
