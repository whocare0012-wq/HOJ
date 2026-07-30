package top.hcode.hoj.common;

import java.util.Locale;

/**
 * The six daily-fortune levels shown on the homepage.
 */
public enum DailyFortuneType {

    GREAT_LUCK("great_luck", "大吉", "#25B864", "运势极佳，万事如意", 1, 100),
    MEDIUM_LUCK("medium_luck", "中吉", "#6FCF97", "运势顺遂，稳中有进", 2, 88),
    SMALL_LUCK("small_luck", "小吉", "#A3D977", "运势尚可，小有收获", 3, 76),
    NEUTRAL("neutral", "中平", "#90A4AE", "运势平平，稳中求进", 4, 64),
    BAD_LUCK("bad_luck", "凶", "#FA8C16", "运势欠佳，谨慎行事", 5, 42),
    GREAT_BAD_LUCK("great_bad_luck", "大凶", "#F5222D", "运势不佳，宜静不宜动", 6, 20);

    private final String code;
    private final String displayName;
    private final String color;
    private final String description;
    private final int sortOrder;
    private final int legacyScore;

    DailyFortuneType(
            String code,
            String displayName,
            String color,
            String description,
            int sortOrder,
            int legacyScore) {
        this.code = code;
        this.displayName = displayName;
        this.color = color;
        this.description = description;
        this.sortOrder = sortOrder;
        this.legacyScore = legacyScore;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getLegacyScore() {
        return legacyScore;
    }

    public static DailyFortuneType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalizedCode = code.trim().toLowerCase(Locale.ROOT);
        for (DailyFortuneType type : values()) {
            if (type.code.equals(normalizedCode)) {
                return type;
            }
        }
        return null;
    }
}
