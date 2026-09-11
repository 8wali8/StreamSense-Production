package com.streamsense.apigateway.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RangePagingTest {

    record Event(String id, long at) {}

    /** A store of events at 1 ms intervals, answering inclusive ranges in ascending order, one page at a time. */
    private static Mono<List<Event>> page(List<Event> store, long from, long to, int size, AtomicInteger calls) {
        calls.incrementAndGet();
        List<Event> page = new ArrayList<>();
        for (Event event : store) {
            if (event.at() >= from && event.at() <= to && page.size() < size) {
                page.add(event);
            }
        }
        return Mono.just(page);
    }

    @Test
    void walksEveryPageAndDropsTheEventRepeatedOnTheCut() {
        List<Event> store = new ArrayList<>();
        for (long at = 1; at <= 7; at++) {
            store.add(new Event("e" + at, at));
        }
        AtomicInteger calls = new AtomicInteger();

        List<Event> all = RangePaging.all(1, 7, 3, (from, to) -> page(store, from, to, 3, calls), Event::at, Event::id)
                .block();

        assertThat(all).extracting(Event::id).containsExactly("e1", "e2", "e3", "e4", "e5", "e6", "e7");
        // [1,2,3] [3,4,5] [5,6,7] [7]: the last page is short, so it ends there.
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void aShortFirstPageIsTheWholeAnswer() {
        List<Event> store = List.of(new Event("a", 10), new Event("b", 20));
        AtomicInteger calls = new AtomicInteger();

        List<Event> all = RangePaging.all(
                        0, 100, 50, (from, to) -> page(store, from, to, 50, calls), Event::at, Event::id)
                .block();

        assertThat(all).hasSize(2);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void aPageThatCannotAdvanceAndTheHardCapBothEndTheWalk() {
        // Every event on the same millisecond: a full page whose cut equals its start.
        List<Event> same = List.of(new Event("x", 5), new Event("y", 5), new Event("z", 5));
        AtomicInteger calls = new AtomicInteger();
        assertThat(RangePaging.all(5, 9, 3, (from, to) -> page(same, from, to, 3, calls), Event::at, Event::id)
                        .block())
                .hasSize(3);
        assertThat(calls.get()).isEqualTo(1);

        // A store that always has more: the cap stops it.
        AtomicInteger endless = new AtomicInteger();
        List<Event> capped = RangePaging.all(
                        0,
                        Long.MAX_VALUE,
                        2,
                        (from, to) -> {
                            endless.incrementAndGet();
                            return Mono.just(List.of(new Event("p" + from, from), new Event("q" + from, from + 1)));
                        },
                        Event::at,
                        Event::id)
                .block();
        assertThat(endless.get()).isEqualTo(RangePaging.MAX_PAGES);
        // Fresh ids on every page here, so nothing is deduplicated: two events per page until the cap.
        assertThat(capped).hasSize(2 * RangePaging.MAX_PAGES);
    }
}
