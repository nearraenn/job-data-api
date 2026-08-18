package com.ata.jobdata.web;

import com.ata.jobdata.query.QueryParams;

import java.util.List;
import java.util.Map;

/**
 * {@code { "data": [...], "meta": {...} }}.
 *
 * <p>The envelope exists so a paginated response can say how many rows matched in total — a bare
 * array cannot, and adding it later would be a breaking change for every client.
 *
 * <p>Rows are maps rather than a fixed DTO because sparse fieldsets let the client choose both which
 * keys appear and in what order.
 */
public record JobDataResponse(List<Map<String, Object>> data, Meta meta) {

    public record Meta(long total, int page, int size, int totalPages) {

        public static Meta of(int total, QueryParams params) {
            return new Meta(total, params.page(), params.size(),
                    (int) Math.ceil((double) total / params.size()));
        }
    }
}
