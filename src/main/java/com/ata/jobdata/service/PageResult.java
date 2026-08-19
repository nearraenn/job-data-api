package com.ata.jobdata.service;

import java.util.List;
import java.util.Map;

/**
 * One page of projected rows plus how many matched in total.
 *
 * <p>Deliberately not the HTTP response shape: the service knows what was found, the controller
 * knows how to present it. Keeping the two apart is what lets the dependency arrow point one way —
 * controller to service, never back.
 *
 * @param data  the rows for the requested page, already narrowed to the requested fields
 * @param total how many rows matched the filters, before paging
 */
public record PageResult(List<Map<String, Object>> data, int total) {
}
