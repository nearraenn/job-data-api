package com.ata.jobdata.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the raw query string into a validated {@link QueryParams}.
 *
 * <p>Anything that is not a reserved parameter is read as a filter, which is what makes
 * {@code ?salary[gte]=120000&gender=Male} work without declaring a method argument per column.
 */
public final class QueryParser {

    public static final String FIELDS = "fields";
    public static final String SORT = "sort";
    public static final String SORT_TYPE = "sort_type";
    public static final String PAGE = "page";
    public static final String SIZE = "size";
    private static final Set<String> RESERVED = Set.of(FIELDS, SORT, SORT_TYPE, PAGE, SIZE);

    /** {@code job_title} or {@code salary[gte]} */
    private static final Pattern FILTER_KEY = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)(?:\\[([A-Za-z]+)])?$");

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    private QueryParser() {}

    public static QueryParams parse(Map<String, String> params) {
        List<QueryParams.Filter> filters = new ArrayList<>();
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (RESERVED.contains(param.getKey().toLowerCase())) {
                continue;
            }
            filters.add(toFilter(param.getKey(), param.getValue()));
        }
        return new QueryParams(
                List.copyOf(filters),
                parseFields(params.get(FIELDS)),
                parseSorts(params.get(SORT), params.get(SORT_TYPE)),
                parsePositiveInt(params.get(PAGE), PAGE, 1, Integer.MAX_VALUE),
                parsePositiveInt(params.get(SIZE), SIZE, DEFAULT_SIZE, MAX_SIZE));
    }

    private static QueryParams.Filter toFilter(String key, String value) {
        Matcher matcher = FILTER_KEY.matcher(key);
        if (!matcher.matches()) {
            throw ApiException.badRequest("INVALID_PARAMETER",
                    "Cannot read query parameter '" + key + "'. Expected <field> or <field>[<operator>]");
        }
        JobField field = JobField.from(matcher.group(1));
        FilterOperator operator = matcher.group(2) == null
                ? FilterOperator.EQ
                : FilterOperator.from(matcher.group(2));
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("MISSING_VALUE", "Filter '" + key + "' has no value");
        }
        return new QueryParams.Filter(field, operator, value);
    }

    /** Order matters: the response keys come out exactly as the client listed them. */
    private static List<JobField> parseFields(String fields) {
        if (fields == null || fields.isBlank()) {
            return List.of();
        }
        Set<JobField> selected = new LinkedHashSet<>();
        for (String name : split(fields)) {
            selected.add(JobField.from(name));
        }
        return List.copyOf(selected);
    }

    private static List<QueryParams.Sort> parseSorts(String sort, String sortType) {
        if (sort == null || sort.isBlank()) {
            return List.of();
        }
        List<String> names = split(sort);
        List<String> directions = sortType == null || sortType.isBlank() ? List.of() : split(sortType);
        if (directions.size() > names.size()) {
            throw ApiException.badRequest("INVALID_SORT",
                    "Got " + directions.size() + " sort_type values for " + names.size() + " sort fields");
        }
        List<QueryParams.Sort> sorts = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            // fewer sort_type values than sort fields: the rest default to ASC
            String direction = i < directions.size() ? directions.get(i) : "ASC";
            sorts.add(new QueryParams.Sort(JobField.from(names.get(i)), isDescending(direction)));
        }
        return List.copyOf(sorts);
    }

    private static boolean isDescending(String direction) {
        if (direction.equalsIgnoreCase("DESC")) {
            return true;
        }
        if (direction.equalsIgnoreCase("ASC")) {
            return false;
        }
        throw ApiException.badRequest("INVALID_SORT_TYPE",
                "Unknown sort_type '" + direction + "'. Expected ASC or DESC");
    }

    private static int parsePositiveInt(String raw, String name, int fallback, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("INVALID_PARAMETER", "Parameter '" + name + "' must be a number");
        }
        if (value < 1 || value > max) {
            throw ApiException.badRequest("INVALID_PARAMETER",
                    "Parameter '" + name + "' must be between 1 and " + max);
        }
        return value;
    }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
