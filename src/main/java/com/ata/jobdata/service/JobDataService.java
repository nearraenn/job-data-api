package com.ata.jobdata.service;

import com.ata.jobdata.controller.JobDataResponse;
import com.ata.jobdata.exception.ApiException;
import com.ata.jobdata.model.JobRecord;
import com.ata.jobdata.query.JobField;
import com.ata.jobdata.query.QueryParams;
import com.ata.jobdata.repository.JobDataRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Filter, then sort, then paginate, then project — in that order, which is what a client expects. */
@Service
public class JobDataService {

    private final JobDataRepository repository;

    public JobDataService(JobDataRepository repository) {
        this.repository = repository;
    }

    public JobDataResponse query(QueryParams params) {
        Predicate<JobRecord> filter = filterOf(params);
        Comparator<JobRecord> sort = sortOf(params);

        List<JobRecord> matched = repository.findAll().stream().filter(filter).toList();
        if (sort != null) {
            matched = matched.stream().sorted(sort).toList();
        }

        int from = Math.min((params.page() - 1) * params.size(), matched.size());
        int to = Math.min(from + params.size(), matched.size());
        List<Map<String, Object>> data = matched.subList(from, to).stream()
                .map(record -> project(record, params.fields()))
                .toList();

        return new JobDataResponse(data, JobDataResponse.Pagination.of(matched.size(), params));
    }

    public Map<String, Object> findById(int id, List<JobField> fields) {
        return repository.findById(id)
                .map(record -> project(record, fields))
                .orElseThrow(() -> ApiException.notFound("RECORD_NOT_FOUND", "No job record with id " + id));
    }

    /** Combining up front means the predicates are built once, not once per row. */
    private static Predicate<JobRecord> filterOf(QueryParams params) {
        return params.filters().stream()
                .map(f -> f.field().predicate(f.operator(), f.value()))
                .reduce(Predicate::and)
                .orElse(record -> true);
    }

    /** Null rather than a no-op comparator, so an unsorted query keeps the file's original order. */
    private static Comparator<JobRecord> sortOf(QueryParams params) {
        return params.sorts().stream()
                .map(s -> s.field().comparator(s.descending()))
                .reduce(Comparator::thenComparing)
                .orElse(null);
    }

    /** An empty fieldset means every field; a LinkedHashMap keeps the client's requested order. */
    private static Map<String, Object> project(JobRecord record, List<JobField> fields) {
        List<JobField> selected = fields.isEmpty() ? List.of(JobField.values()) : fields;
        Map<String, Object> projected = new LinkedHashMap<>();
        for (JobField field : selected) {
            projected.put(field.apiName(), field.valueOf(record));
        }
        return projected;
    }
}
