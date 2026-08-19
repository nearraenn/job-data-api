package com.ata.jobdata.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Drives the three URLs from the exercise brief against the real dataset. */
@SpringBootTest
@AutoConfigureMockMvc
class JobDataControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void filtersBySalary() throws Exception {
        List<Map<String, Object>> data = data("/api/job_data?salary[gte]=120000&size=200");

        assertThat(data).isNotEmpty();
        assertThat(data).allSatisfy(row ->
                assertThat(((Number) row.get("salary")).longValue()).isGreaterThanOrEqualTo(120_000L));
        assertThat(data)
                .as("normalised rows must be matched too, not just the ones typed as bare digits")
                .anySatisfy(row -> assertThat((String) row.get("salary_raw")).startsWith("$"));
    }

    @Test
    void combinesFiltersWithAnd() throws Exception {
        List<Map<String, Object>> data = data(
                "/api/job_data?salary[gte]=100000&salary[lte]=150000&gender=Female&job_title[like]=engineer&size=200");

        assertThat(data).isNotEmpty();
        assertThat(data).allSatisfy(row -> {
            assertThat(((Number) row.get("salary")).longValue()).isBetween(100_000L, 150_000L);
            assertThat((String) row.get("gender")).isEqualToIgnoringCase("Female");
            assertThat((String) row.get("job_title")).containsIgnoringCase("engineer");
        });
    }

    @Test
    void returnsOnlyTheRequestedFieldsInTheRequestedOrder() throws Exception {
        List<Map<String, Object>> data = data("/api/job_data?fields=job_title,gender,salary");

        assertThat(data).isNotEmpty();
        assertThat(data).allSatisfy(row ->
                assertThat(row.keySet()).containsExactly("job_title", "gender", "salary"));
    }

    @Test
    void sortsDescendingAndKeepsMissingValuesLast() throws Exception {
        List<Map<String, Object>> data = data("/api/job_data?sort=job_title&sort_type=DESC&fields=job_title&size=200");

        List<String> titles = data.stream().map(row -> (String) row.get("job_title")).toList();
        assertThat(titles).isSortedAccordingTo(
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER.reversed()));
    }

    @Test
    void sortsNumericallyNotLexicographically() throws Exception {
        List<Map<String, Object>> data = data("/api/job_data?sort=salary&sort_type=ASC&fields=salary&size=200");

        List<Long> salaries = data.stream()
                .map(row -> row.get("salary") == null ? null : ((Number) row.get("salary")).longValue())
                .toList();
        assertThat(salaries).isSortedAccordingTo(Comparator.nullsLast(Comparator.naturalOrder()));
    }

    @Test
    void reportsTotalMatchesAlongsideThePage() throws Exception {
        mockMvc.perform(get("/api/job_data?gender=Female&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.pagination.total").value(org.hamcrest.Matchers.greaterThan(5)))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.total_pages").value(org.hamcrest.Matchers.greaterThan(1)));
    }

    @Test
    void firstPageHasNoPrevAndDoesHaveNext() throws Exception {
        mockMvc.perform(get("/api/job_data?gender=Female&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.prev").doesNotExist())
                .andExpect(jsonPath("$.pagination.next", org.hamcrest.Matchers.containsString("page=2")))
                .andExpect(jsonPath("$.pagination.next", org.hamcrest.Matchers.containsString("gender=Female")))
                .andExpect(jsonPath("$.pagination.next", org.hamcrest.Matchers.containsString("size=5")));
    }

    @Test
    void lastPageHasNoNext() throws Exception {
        mockMvc.perform(get("/api/job_data?gender=Female&size=200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total_pages").value(1))
                .andExpect(jsonPath("$.pagination.next").doesNotExist())
                .andExpect(jsonPath("$.pagination.prev").doesNotExist());
    }

    @Test
    void returnsASingleRecordById() throws Exception {
        mockMvc.perform(get("/api/job_data/1?fields=id,job_title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.job_title").isNotEmpty());
    }

    @Test
    void rejectsAnUnknownFilterFieldWith400() throws Exception {
        mockMvc.perform(get("/api/job_data?salaryy[gte]=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_FIELD"));
    }

    @Test
    void rejectsAnUnknownRecordWith404() throws Exception {
        mockMvc.perform(get("/api/job_data/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
    }

    private List<Map<String, Object>> data(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        return data;
    }
}
