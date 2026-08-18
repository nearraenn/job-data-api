package com.ata.jobdata.query;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryParserTest {

    @Test
    void readsBracketOperatorsAndTreatsABareParamAsEquals() {
        QueryParams params = QueryParser.parse(ordered("salary[gte]", "120000", "gender", "Male"));

        assertThat(params.filters()).containsExactly(
                new QueryParams.Filter(JobField.SALARY, FilterOperator.GTE, "120000"),
                new QueryParams.Filter(JobField.GENDER, FilterOperator.EQ, "Male"));
    }

    @Test
    void keepsSparseFieldsInTheOrderRequested() {
        QueryParams params = QueryParser.parse(Map.of("fields", "job_title,gender,salary"));

        assertThat(params.fields()).containsExactly(JobField.JOB_TITLE, JobField.GENDER, JobField.SALARY);
    }

    @Test
    void defaultsMissingSortTypesToAscending() {
        QueryParams params = QueryParser.parse(ordered("sort", "salary,job_title", "sort_type", "DESC"));

        assertThat(params.sorts()).containsExactly(
                new QueryParams.Sort(JobField.SALARY, true),
                new QueryParams.Sort(JobField.JOB_TITLE, false));
    }

    @Test
    void appliesPagingDefaults() {
        QueryParams params = QueryParser.parse(Map.of());

        assertThat(params.page()).isEqualTo(1);
        assertThat(params.size()).isEqualTo(QueryParser.DEFAULT_SIZE);
    }

    @Test
    void rejectsAnUnknownFieldRatherThanIgnoringIt() {
        assertThatThrownBy(() -> QueryParser.parse(Map.of("salaryy", "1")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("UNKNOWN_FIELD"));
    }

    @Test
    void rejectsAnUnknownOperator() {
        assertThatThrownBy(() -> QueryParser.parse(Map.of("salary[between]", "1")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("UNKNOWN_OPERATOR"));
    }

    @Test
    void rejectsAnUnknownSortType() {
        assertThatThrownBy(() -> QueryParser.parse(ordered("sort", "salary", "sort_type", "SIDEWAYS")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("INVALID_SORT_TYPE"));
    }

    @Test
    void rejectsAPageSizeBeyondTheCap() {
        assertThatThrownBy(() -> QueryParser.parse(Map.of("size", String.valueOf(QueryParser.MAX_SIZE + 1))))
                .isInstanceOf(ApiException.class);
    }

    /** Filter order is asserted above, so the map has to preserve insertion order. */
    private static Map<String, String> ordered(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
