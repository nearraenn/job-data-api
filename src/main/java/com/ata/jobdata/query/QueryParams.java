package com.ata.jobdata.query;

import java.util.List;

/**
 * A validated query. Every field name here already exists in {@link JobField}, so the service can
 * apply it without re-checking anything.
 *
 * @param filters ANDed together
 * @param fields  the sparse fieldset, in the order the client asked for; empty means "all fields"
 * @param sorts   applied in order, first one wins ties
 * @param page    1-based
 */
public record QueryParams(List<Filter> filters, List<JobField> fields, List<Sort> sorts, int page, int size) {

    public record Filter(JobField field, FilterOperator operator, String value) {}

    public record Sort(JobField field, boolean descending) {}
}
