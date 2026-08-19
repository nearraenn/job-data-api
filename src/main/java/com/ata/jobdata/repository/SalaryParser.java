package com.ata.jobdata.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the free-text {@code Salary} column of the survey into a comparable number.
 *
 * <p>The source data is user-typed, so a single column holds all of these:
 * {@code "122000"}, {@code "83,000"}, {@code "$120,000"}, {@code "€60,000"}, {@code "70k"},
 * {@code "$30/hr"}, {@code "110000-120000"}, {@code "5.5 Million JPY"}, {@code "1 rare pepe"},
 * {@code "-"}, {@code ""}. Without this step {@code ?salary[gte]=120000} cannot work at all.
 *
 * <p>ponytail: values are normalised to an annual amount but are <em>not</em> converted to a common
 * currency — {@code €60,000} and {@code 60000 USD} both compare as 60000. Doing it properly needs an
 * FX rate table plus an as-of date per row, which is out of scope for a read-only exercise. The
 * detected currency is returned alongside the amount so a client can decide for itself; upgrade path
 * is to convert to a base currency here, at ingest, and keep the original in {@code salaryRaw}.
 */
final class SalaryParser {

    /** Annual amount (null when the text holds no usable number) plus the ISO code we could detect. */
    record Money(Long amount, String currency) {}

    private static final Money NONE = new Money(null, null);

    /** A run of digits that may contain grouping separators, e.g. {@code 15,00,000} or {@code 23 000}. */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,.\\u00A0 ]*\\d|\\d");

    /** Longer alternatives first — {@code EURO} must win over {@code EUR}. */
    private static final Pattern CURRENCY_CODE = Pattern.compile(
            "(?i)(?<![A-Za-z])(EURO|EUR|USD|GBP|CAD|CDN|AUD|NZD|CHF|SEK|NOK|DKK|PLN|CZK|"
                    + "JPY|YEN|CNY|RMB|HKD|SGD|INR|RS|BRL|MXN|ZAR|ILS|RUB|TRY|KRW)(?![A-Za-z])");

    private static final Pattern HOURLY = Pattern.compile("(?i)/\\s*h(r|our)?\\b|per\\s+hour|\\bhourly\\b|\\bhour\\b");
    private static final Pattern DAILY = Pattern.compile("(?i)/\\s*day\\b|per\\s+day|\\bdaily\\b");
    /** A {@code k}/{@code M} multiplier written right after the number, e.g. {@code 70k}, {@code 5.5 Million}. */
    private static final Pattern MULTIPLIER = Pattern.compile("(?i)^\\s*(k|m|mn|million)(?![A-Za-z])");

    private static final int WORK_HOURS_PER_YEAR = 2080; // 40h x 52wk
    private static final int WORK_DAYS_PER_YEAR = 260;   // 5d x 52wk
    /** Below this an "annual salary" is really an hourly rate, a typo, or a joke — safer as unknown. */
    private static final long MIN_PLAUSIBLE_ANNUAL = 1_000L;

    private SalaryParser() {}

    static Money parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        Matcher number = NUMBER.matcher(raw);
        if (!number.find()) {
            return NONE; // "-", "n/a", pure prose
        }
        BigDecimal amount = toDecimal(number.group());
        if (amount == null) {
            return NONE;
        }
        amount = amount.multiply(BigDecimal.valueOf(multiplierAfter(raw, number.end())));

        if (HOURLY.matcher(raw).find()) {
            amount = amount.multiply(BigDecimal.valueOf(WORK_HOURS_PER_YEAR));
        } else if (DAILY.matcher(raw).find()) {
            amount = amount.multiply(BigDecimal.valueOf(WORK_DAYS_PER_YEAR));
        }

        long annual = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        String currency = detectCurrency(raw);
        return annual < MIN_PLAUSIBLE_ANNUAL ? new Money(null, currency) : new Money(annual, currency);
    }

    /** Grouping separators are dropped; {@code .} is always a decimal point in this dataset. */
    private static BigDecimal toDecimal(String token) {
        String cleaned = token.replaceAll("[,\\u00A0 ]", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null; // e.g. a stray "1.2.3"
        }
    }

    private static long multiplierAfter(String raw, int numberEnd) {
        Matcher m = MULTIPLIER.matcher(raw.substring(numberEnd));
        if (!m.find()) {
            return 1L;
        }
        return m.group(1).equalsIgnoreCase("k") ? 1_000L : 1_000_000L;
    }

    /** An explicit code beats a symbol: {@code "$84,500 AUD"} is AUD, not USD. */
    private static String detectCurrency(String raw) {
        Matcher code = CURRENCY_CODE.matcher(raw);
        if (code.find()) {
            return normaliseCode(code.group(1).toUpperCase());
        }
        for (int i = 0; i < raw.length(); i++) {
            switch (raw.charAt(i)) {
                case '$' -> { return "USD"; } // ponytail: bare "$" is assumed USD; CAD/AUD/NZD rows say so explicitly
                case '€' -> { return "EUR"; }
                case '£' -> { return "GBP"; }
                case '¥' -> { return "JPY"; }
                case '₹' -> { return "INR"; }
                default -> { }
            }
        }
        return null;
    }

    private static String normaliseCode(String code) {
        return switch (code) {
            case "CDN" -> "CAD";
            case "EURO" -> "EUR";
            case "YEN" -> "JPY";
            case "RMB" -> "CNY";
            case "RS" -> "INR";
            default -> code;
        };
    }
}
