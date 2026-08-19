package com.ata.jobdata.controller;

import com.ata.jobdata.query.JobField;
import com.ata.jobdata.query.QueryParams;
import com.ata.jobdata.query.QueryParser;
import com.ata.jobdata.service.JobDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/job_data")
@Tag(name = "Job data", description = "Read-only access to the salary survey dataset")
public class JobDataController {

    private final JobDataService service;

    public JobDataController(JobDataService service) {
        this.service = service;
    }

    /**
     * Filters are read straight off the query string rather than declared one argument per column,
     * so {@code ?salary[gte]=120000&gender=Male} works for every field in {@link JobField}.
     */
    @GetMapping
    @Operation(summary = "List job records",
            description = "Filter with <field>[<operator>]=<value>, choose columns with fields=, order with sort= and sort_type=.")
    @Parameters({
            @Parameter(name = "fields", in = ParameterIn.QUERY, example = "job_title,gender,salary",
                    description = "Sparse fieldset — only these keys are returned, in this order"),
            @Parameter(name = "sort", in = ParameterIn.QUERY, example = "job_title",
                    description = "Comma-separated field names"),
            @Parameter(name = "sort_type", in = ParameterIn.QUERY, example = "DESC",
                    description = "ASC or DESC, one per sort field (missing ones default to ASC)"),
            @Parameter(name = "page", in = ParameterIn.QUERY, example = "1", description = "1-based page number"),
            @Parameter(name = "size", in = ParameterIn.QUERY, example = "50", description = "Rows per page, max 200"),
            @Parameter(name = "salary[gte]", in = ParameterIn.QUERY, example = "120000",
                    description = "Example filter. Operators: eq, ne, gt, gte, lt, lte, like, in")
    })
    public JobDataResponse list(@RequestParam Map<String, String> params, HttpServletRequest request) {
        JobDataResponse response = service.query(QueryParser.parse(params));
        JobDataResponse.Pagination pagination = response.pagination();
        String next = pagination.page() < pagination.totalPages()
                ? pageUrl(request, params, pagination.page() + 1) : null;
        String prev = pagination.page() > 1 ? pageUrl(request, params, pagination.page() - 1) : null;
        return new JobDataResponse(response.data(), pagination.withLinks(next, prev));
    }

    /** Same filters/sort/fields as the current request, just a different page — nothing for the client to rebuild. */
    private static String pageUrl(HttpServletRequest request, Map<String, String> params, int page) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        params.forEach(query::add);
        query.set(QueryParser.PAGE, String.valueOf(page));
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replaceQueryParams(query)
                .build()
                .toUriString();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one job record by id")
    public Map<String, Object> byId(@PathVariable int id,
                                    @RequestParam(name = "fields", required = false) String fields) {
        QueryParams params = QueryParser.parse(fields == null ? Map.of() : Map.of("fields", fields));
        return service.findById(id, params.fields());
    }
}
