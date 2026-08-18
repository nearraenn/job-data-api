package com.ata.jobdata.query;

import java.util.Arrays;
import java.util.stream.Collectors;

/** The comparison in {@code ?salary[gte]=120000}. Bare {@code ?gender=Male} means {@link #EQ}. */
public enum FilterOperator {

    EQ("eq"),
    NE("ne"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    /** Case-insensitive "contains", for strings only. */
    LIKE("like"),
    /** Matches any value in a comma-separated list. */
    IN("in");

    private final String key;

    FilterOperator(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Turns the result of a {@code compareTo} into a match, so numbers and strings share one path. */
    public boolean matches(int comparison) {
        return switch (this) {
            case EQ -> comparison == 0;
            case NE -> comparison != 0;
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
            case LIKE, IN -> throw new IllegalStateException(key + " is not a comparison");
        };
    }

    public static FilterOperator from(String key) {
        return Arrays.stream(values())
                .filter(op -> op.key.equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_OPERATOR",
                        "Unknown filter operator '" + key + "'. Supported: " + keys()));
    }

    public static String keys() {
        return Arrays.stream(values()).map(FilterOperator::key).collect(Collectors.joining(", "));
    }
}
