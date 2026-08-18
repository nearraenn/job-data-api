package com.ata.jobdata.repository;

import com.ata.jobdata.model.JobRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and normalises the survey file once at startup, then serves it from an immutable list.
 *
 * <p>The dataset is static and fits comfortably in memory (~3.8k rows), so parsing per request — or
 * standing up a database for read-only data that never changes — would buy nothing.
 */
@Repository
public class InMemoryJobDataRepository implements JobDataRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryJobDataRepository.class);

    private final List<JobRecord> records;

    public InMemoryJobDataRepository(ObjectMapper objectMapper,
                                     @Value("${jobdata.source}") Resource source) {
        this.records = load(objectMapper, source);
        log.info("Loaded {} job records from {} ({} with a usable salary)",
                records.size(), source.getFilename(), records.stream().filter(r -> r.salary() != null).count());
    }

    @Override
    public List<JobRecord> findAll() {
        return records;
    }

    @Override
    public Optional<JobRecord> findById(int id) {
        // ids are the 1-based load order, so this is a direct index rather than a scan
        return id >= 1 && id <= records.size() ? Optional.of(records.get(id - 1)) : Optional.empty();
    }

    private static List<JobRecord> load(ObjectMapper objectMapper, Resource source) {
        try (InputStream in = source.getInputStream()) {
            List<RawJobRow> rows = objectMapper.readValue(
                    StreamUtils.copyToByteArray(in), new TypeReference<List<RawJobRow>>() {});
            List<JobRecord> loaded = new ArrayList<>(rows.size());
            for (RawJobRow row : rows) {
                if (!row.isEmpty()) {
                    loaded.add(row.toJobRecord(loaded.size() + 1));
                }
            }
            return List.copyOf(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read job data from " + source, e);
        }
    }
}
