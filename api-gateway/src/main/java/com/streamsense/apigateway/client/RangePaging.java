package com.streamsense.apigateway.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import reactor.core.publisher.Mono;

/**
 * Walks a time-ordered range endpoint page by page until it comes back short, so a busy session's
 * timeline holds every event rather than the first page. Pages are cut at the last event's timestamp
 * (the endpoints order ascending and their bounds are inclusive), so an event on the cut is fetched
 * again and dropped by id. A hard cap on pages keeps a runaway session bounded.
 */
public final class RangePaging {

    /** Enough for a twelve-hour stream at one event a second; beyond that the timeline is a sample. */
    static final int MAX_PAGES = 25;

    private RangePaging() {}

    public static <T> Mono<List<T>> all(
            long from,
            long to,
            int pageSize,
            BiFunction<Long, Long, Mono<List<T>>> page,
            ToLongFunction<T> timestamp,
            Function<T, String> id) {
        return collect(from, to, pageSize, page, timestamp, id, new LinkedHashMap<>(), 0);
    }

    private static <T> Mono<List<T>> collect(
            long from,
            long to,
            int pageSize,
            BiFunction<Long, Long, Mono<List<T>>> page,
            ToLongFunction<T> timestamp,
            Function<T, String> id,
            Map<String, T> collected,
            int pages) {
        if (from > to || pages >= MAX_PAGES) {
            return Mono.just(new ArrayList<>(collected.values()));
        }
        return page.apply(from, to).flatMap(events -> {
            for (T event : events) {
                collected.putIfAbsent(id.apply(event), event);
            }
            if (events.size() < pageSize) {
                return Mono.just(new ArrayList<>(collected.values()));
            }
            long last = timestamp.applyAsLong(events.get(events.size() - 1));
            // A page made entirely of one timestamp cannot advance; take what there is.
            if (last <= from) {
                return Mono.just(new ArrayList<>(collected.values()));
            }
            return collect(last, to, pageSize, page, timestamp, id, collected, pages + 1);
        });
    }
}
