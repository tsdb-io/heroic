package com.spotify.heroic.grammar;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.spotify.heroic.filter.Filter;
import com.spotify.heroic.filter.FilterFactory;
import com.spotify.heroic.model.DateRange;

public class QueryParserTest {
    private CoreQueryParser parser;
    private FilterFactory filters;

    @Before
    public void setupFilters() {
        filters = Mockito.mock(FilterFactory.class);
        parser = new CoreQueryParser(filters);
    }

    @Test
    public void testSelect() {
        final AggregationValue average = new AggregationValue("average", ImmutableList.<Value> of(new DiffValue(
                TimeUnit.HOURS, 30)), ImmutableMap.<String, Value> of());

        final AggregationValue sum = new AggregationValue("sum", ImmutableList.<Value> of(new DiffValue(TimeUnit.HOURS,
                30)), ImmutableMap.<String, Value> of());

        final AggregationValue group = new AggregationValue("group", ImmutableList.<Value> of(new ListValue(
                ImmutableList.<Value> of(new StringValue("host"))), average), ImmutableMap.<String, Value> of());

        final AggregationValue chain = new AggregationValue("chain", ImmutableList.<Value> of(group, sum),
                ImmutableMap.<String, Value> of());

        Assert.assertEquals(null, parser.parse(CoreQueryParser.SELECT, "all").getAggregation());
        Assert.assertEquals(chain, parser.parse(CoreQueryParser.SELECT, "chain(group([host], average(30H)), sum(30H))")
                .getAggregation());
    }

    @Test(expected = ParseException.class)
    public void testInvalidSelect() {
        parser.parse(CoreQueryParser.SELECT, "1");
    }

    @Test
    public void testValueExpr() {
        Assert.assertEquals("foobar", parser.parse(CoreQueryParser.VALUE_EXPR, "foo + bar").cast(String.class));
        Assert.assertEquals(new DiffValue(TimeUnit.HOURS, 7),
                parser.parse(CoreQueryParser.VALUE_EXPR, "3H + 4H").cast(DiffValue.class));
        Assert.assertEquals(new DiffValue(TimeUnit.MINUTES, 59), parser.parse(CoreQueryParser.VALUE_EXPR, "1H - 1m")
                .cast(DiffValue.class));
        Assert.assertEquals(new DiffValue(TimeUnit.MINUTES, 59), parser.parse(CoreQueryParser.VALUE_EXPR, "119m - 1H")
                .cast(DiffValue.class));
        Assert.assertEquals(new ListValue(ImmutableList.<Value> of(new IntValue(1l), new IntValue(2l))),
                parser.parse(CoreQueryParser.VALUE_EXPR, "[1] + [2]").cast(ListValue.class));
    }

    @Test
    public void testFrom() {
        Assert.assertEquals(FromDSL.SERIES, parser.parse(CoreQueryParser.FROM, "series"));
        Assert.assertEquals(FromDSL.EVENTS, parser.parse(CoreQueryParser.FROM, "events"));
        // absolute
        Assert.assertEquals(new FromDSL(QuerySource.SERIES, new DateRange(0, 1234 + 4321)),
                parser.parse(CoreQueryParser.FROM, "series(0, 1234 + 4321)"));
        // relative
        Assert.assertEquals(new FromDSL(QuerySource.SERIES, new DateRange(0, 1000)),
                parser.parse(CoreQueryParser.FROM, "series(1000ms)", 1000));
    }

    @Test(expected = ParseException.class)
    public void testInvalidGrammar() {
        parser.parse(CoreQueryParser.QUERY, "select ~ from series");
    }

    @Test(expected = ParseException.class)
    public void testInvalidSyntax() {
        parser.parse(CoreQueryParser.QUERY, "select \"some string\" from series");
    }

    @Test
    public void testFilter() {
        final Filter.MatchTag matchTag = Mockito.mock(Filter.MatchTag.class);
        final Filter.And and = Mockito.mock(Filter.And.class);
        final Filter optimized = Mockito.mock(Filter.class);

        Mockito.when(filters.matchTag(Mockito.any(String.class), Mockito.any(String.class))).thenReturn(matchTag);
        Mockito.when(filters.and(matchTag, matchTag)).thenReturn(and);
        Mockito.when(and.optimize()).thenReturn(optimized);

        Assert.assertEquals(optimized, parser.parse(CoreQueryParser.FILTER, "a=b and c=d"));

        Mockito.verify(filters, Mockito.times(2)).matchTag(Mockito.any(String.class), Mockito.any(String.class));
        Mockito.verify(filters).and(matchTag, matchTag);
        Mockito.verify(and).optimize();
    }
}