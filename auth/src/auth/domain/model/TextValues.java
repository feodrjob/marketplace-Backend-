package auth.domain.model;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TextValues {

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
