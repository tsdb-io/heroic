package com.spotify.heroic.metadata.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.spotify.heroic.async.CancelReason;
import com.spotify.heroic.async.Reducer;
import com.spotify.heroic.model.Series;

@Data
public class FindSeries {
    public static final FindSeries EMPTY = new FindSeries(new HashSet<Series>(), 0, 0);

    private final Set<Series> series;
    private final int size;
    private final int duplicates;

    @Slf4j
    public static class SelfReducer implements Reducer<FindSeries, FindSeries> {
        @Override
        public FindSeries resolved(Collection<FindSeries> results, Collection<Exception> errors,
                Collection<CancelReason> cancelled) throws Exception {
            for (final Exception e : errors)
                log.error("Query failed", e);

            if (!errors.isEmpty() || !cancelled.isEmpty())
                throw new Exception("Query failed");

            final Set<Series> series = new HashSet<Series>();
            int size = 0;
            int duplicates = 0;

            for (final FindSeries result : results) {
                for (final Series s : result.getSeries()) {
                    if (series.add(s)) {
                        duplicates += 1;
                    }
                }

                duplicates += result.getDuplicates();
                size += result.getSize();
            }

            return new FindSeries(series, size, duplicates);
        }
    };

    private static final SelfReducer reducer = new SelfReducer();

    public static Reducer<FindSeries, FindSeries> reduce() {
        return reducer;
    }
}