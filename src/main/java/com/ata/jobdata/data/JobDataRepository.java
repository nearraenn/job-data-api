package com.ata.jobdata.data;

import com.ata.jobdata.domain.JobRecord;

import java.util.List;
import java.util.Optional;

/**
 * Source of job records. The dataset is static and small, so the only implementation keeps it in
 * memory — but the service depends on this interface, so swapping in a JPA/JDBC implementation later
 * touches nothing above it.
 */
public interface JobDataRepository {

    List<JobRecord> findAll();

    Optional<JobRecord> findById(int id);
}
