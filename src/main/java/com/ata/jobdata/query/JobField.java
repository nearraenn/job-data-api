package com.ata.jobdata.query;

import com.ata.jobdata.exception.ApiException;
import com.ata.jobdata.model.JobRecord;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The single registry of queryable fields: API name, type, and how to read the value off a record.
 *
 * <p>All three features of the exercise are driven from here — filtering builds a {@link Predicate},
 * sorting builds a {@link Comparator}, sparse fieldsets read values in the order requested, and any
 * name that is not listed here is rejected with 400 on all three paths. Exposing a new column is one
 * line in this enum and no change to the controller or service.
 */
public enum JobField {

    ID("id", Type.NUMBER, JobRecord::id),
    TIMESTAMP("timestamp", Type.STRING, JobRecord::timestamp),
    EMPLOYER("employer", Type.STRING, JobRecord::employer),
    LOCATION("location", Type.STRING, JobRecord::location),
    JOB_TITLE("job_title", Type.STRING, JobRecord::jobTitle),
    YEARS_AT_EMPLOYER("years_at_employer", Type.NUMBER, JobRecord::yearsAtEmployer),
    YEARS_OF_EXPERIENCE("years_of_experience", Type.NUMBER, JobRecord::yearsOfExperience),
    SALARY("salary", Type.NUMBER, JobRecord::salary),
    SALARY_CURRENCY("salary_currency", Type.STRING, JobRecord::salaryCurrency),
    SALARY_RAW("salary_raw", Type.STRING, JobRecord::salaryRaw),
    SIGNING_BONUS("signing_bonus", Type.STRING, JobRecord::signingBonus),
    ANNUAL_BONUS("annual_bonus", Type.STRING, JobRecord::annualBonus),
    ANNUAL_STOCK_VALUE("annual_stock_value", Type.STRING, JobRecord::annualStockValue),
    GENDER("gender", Type.STRING, JobRecord::gender),
    ADDITIONAL_COMMENTS("additional_comments", Type.STRING, JobRecord::additionalComments);

    public enum Type { STRING, NUMBER }

    private final String apiName;
    private final Type type;
    private final Function<JobRecord, Object> extractor;

    JobField(String apiName, Type type, Function<JobRecord, Object> extractor) {
        this.apiName = apiName;
        this.type = type;
        this.extractor = extractor;
    }

    public String apiName() {
        return apiName;
    }

    public Object valueOf(JobRecord record) {
        return extractor.apply(record);
    }

    /**
     * Missing values never match. This follows SQL's treatment of NULL: a row whose salary could not
     * be parsed is not "less than 120000", it is unknown, so it stays out of every numeric filter.
     */
    public Predicate<JobRecord> predicate(FilterOperator operator, String value) {
        if (operator == FilterOperator.IN) {
            List<String> wanted = Arrays.stream(value.split(",")).map(String::trim).map(String::toLowerCase).toList();
            return record -> {
                Object actual = valueOf(record);
                return actual != null && wanted.contains(String.valueOf(actual).toLowerCase());
            };
        }
        if (type == Type.NUMBER) {
            if (operator == FilterOperator.LIKE) {
                throw ApiException.badRequest("UNSUPPORTED_OPERATOR",
                        "Operator 'like' is not supported for numeric field '" + apiName + "'");
            }
            double target = asNumber(value);
            return record -> {
                Number actual = (Number) valueOf(record);
                return actual != null && operator.matches(Double.compare(actual.doubleValue(), target));
            };
        }
        if (operator == FilterOperator.LIKE) {
            String needle = value.toLowerCase();
            return record -> {
                String actual = (String) valueOf(record);
                return actual != null && actual.toLowerCase().contains(needle);
            };
        }
        return record -> {
            String actual = (String) valueOf(record);
            return actual != null && operator.matches(actual.compareToIgnoreCase(value));
        };
    }

    /**
     * Missing values sort last in <em>both</em> directions — otherwise {@code sort_type=DESC} would
     * fill the first page with rows whose salary could not be parsed.
     */
    public Comparator<JobRecord> comparator(boolean descending) {
        Comparator<Object> values = type == Type.NUMBER
                ? Comparator.comparingDouble(value -> ((Number) value).doubleValue())
                : Comparator.comparing(String.class::cast, String.CASE_INSENSITIVE_ORDER);
        return Comparator.comparing(this::valueOf, Comparator.nullsLast(descending ? values.reversed() : values));
    }

    public static JobField from(String apiName) {
        return Arrays.stream(values())
                .filter(field -> field.apiName.equalsIgnoreCase(apiName))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_FIELD",
                        "Unknown field '" + apiName + "'. Available: " + apiNames()));
    }

    public static String apiNames() {
        return Arrays.stream(values()).map(JobField::apiName).collect(Collectors.joining(", "));
    }

    private double asNumber(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("INVALID_VALUE",
                    "Field '" + apiName + "' is numeric but got '" + value + "'");
        }
    }
}
