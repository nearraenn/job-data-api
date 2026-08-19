package com.ata.jobdata.controller;

import com.ata.jobdata.query.QueryParams;

import java.util.List;
import java.util.Map;

/**
 * {@code { "data": [...], "pagination": {...} }}.
 *
 * <p>The envelope exists so a paginated response can say how many rows matched in total — a bare
 * array cannot, and adding it later would be a breaking change for every client.
 *
 * <p>Rows are maps rather than a fixed DTO because sparse fieldsets let the client choose both which
 * keys appear and in what order.
 */
public record JobDataResponse(List<Map<String, Object>> data, Pagination pagination) {

    /**
     * Named for exactly what it holds — every field here is about paging, nothing else. {@code next}
     * and {@code prev} are ready-to-fetch URLs (the same filter/sort/fields, just a different page)
     * so a client never has to reconstruct the query string itself; {@link #of} builds everything but
     * those two, since only the controller knows the request URL.
     */
    public record Pagination(long total, int page, int size, int totalPages, String next, String prev) {

        public static Pagination of(int total, QueryParams params) {
            int totalPages = (int) Math.ceil((double) total / params.size());
            return new Pagination(total, params.page(), params.size(), totalPages, null, null);
        }

        public Pagination withLinks(String next, String prev) {
            return new Pagination(total, page, size, totalPages, next, prev);
        }
    }
}
