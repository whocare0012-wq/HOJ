package top.hcode.hoj.agent;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSubmissionFilterTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static AgentSubmissionFilter parse(String... keyValues) {
        Map<String, String[]> raw = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            raw.merge(keyValues[i], new String[]{keyValues[i + 1]}, (a, b) -> {
                String[] merged = Arrays.copyOf(a, a.length + b.length);
                System.arraycopy(b, 0, merged, a.length, b.length);
                return merged;
            });
        }
        return AgentSubmissionFilter.parse(new AgentParams(raw, AgentSubmissionFilter.PARAMS), SHANGHAI);
    }

    private static Instant instant(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void dateCoversTheWholeDayInSiteTimezone() {
        AgentSubmissionFilter filter = parse("date", "2026-11-01");
        assertEquals(instant("2026-10-31T16:00:00Z"), filter.getFrom().toInstant());
        assertEquals(instant("2026-11-01T16:00:00Z"), filter.getTo().toInstant());
    }

    @Test
    void dateOnlyUpperBoundIncludesThatDay() {
        AgentSubmissionFilter filter = parse("from", "2026-11-01", "to", "2026-11-07");
        assertEquals(instant("2026-10-31T16:00:00Z"), filter.getFrom().toInstant());
        assertEquals(instant("2026-11-07T16:00:00Z"), filter.getTo().toInstant());
    }

    @Test
    void acceptsLocalAndOffsetDateTimes() {
        assertEquals(instant("2026-11-01T00:30:00Z"),
                AgentSubmissionFilter.parseBound("from", "2026-11-01 08:30", SHANGHAI, false).toInstant());
        assertEquals(instant("2026-11-01T08:30:00Z"),
                AgentSubmissionFilter.parseBound("from", "2026-11-01T08:30:00Z", SHANGHAI, false).toInstant());
        assertEquals(instant("2026-11-01T08:30:00Z"),
                AgentSubmissionFilter.parseBound("to", "2026-11-01T16:30:00+08:00", SHANGHAI, true).toInstant());
    }

    @Test
    void rejectsInvalidTimeArguments() {
        assertThrows(AgentApiException.class, () -> parse("from", "yesterday"));
        assertThrows(AgentApiException.class, () -> parse("date", "2026-11-01", "from", "2026-10-01"));
        assertThrows(AgentApiException.class, () -> parse("from", "2026-11-02", "to", "2026-11-01T00:00"));
    }

    @Test
    void statusAcceptsShortNamesFullNamesAndCodes() {
        assertEquals(Arrays.asList(0, -1, -2, 1),
                AgentSubmissionFilter.parseStatuses(Arrays.asList("AC", "wrong answer", "-2", "TLE")));
        assertEquals(Collections.singletonList(8),
                AgentSubmissionFilter.parseStatuses(Arrays.asList("pa", "Partial_Accepted")));
        assertThrows(AgentApiException.class,
                () -> AgentSubmissionFilter.parseStatuses(Collections.singletonList("great")));
        assertEquals("Wrong Answer", AgentSubmissionFilter.statusName(-1));
        assertNull(AgentSubmissionFilter.statusName(null));
    }

    @Test
    void conditionsAlwaysExcludeUnfinishedContestsAndBindEveryValue() {
        AgentSubmissionFilter filter = parse(
                "username", "alice,bob", "username", "carol' OR '1'='1",
                "status", "AC", "group_id", "7", "date", "2026-11-01");
        StringBuilder sql = new StringBuilder("WHERE 1 = 1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        Timestamp now = new Timestamp(0);
        filter.appendConditions(sql, args, now);

        String text = sql.toString();
        assertTrue(text.contains("(j.cid = 0 OR (c.end_time IS NOT NULL AND c.end_time <= :now))"));
        assertTrue(text.contains("j.username IN (:usernames)"));
        assertTrue(text.contains("gm.gid = :groupId AND gm.auth IN (3, 4, 5)"));
        assertTrue(text.contains("j.submit_time >= :fromTime") && text.contains("j.submit_time < :toTime"));
        // 用户输入只出现在绑定参数中，不会拼进 SQL
        assertFalse(text.contains("carol"));
        assertEquals(Arrays.asList("alice", "bob", "carol' OR '1'='1"), args.getValue("usernames"));
        assertEquals(Collections.singletonList(0), args.getValue("statuses"));
        assertEquals(7L, args.getValue("groupId"));
        assertEquals(now, args.getValue("now"));
    }

    @Test
    void noFilterStillExcludesUnfinishedContests() {
        StringBuilder sql = new StringBuilder();
        MapSqlParameterSource args = new MapSqlParameterSource();
        parse().appendConditions(sql, args, new Timestamp(0));
        assertEquals(" AND (j.cid = 0 OR (c.end_time IS NOT NULL AND c.end_time <= :now))", sql.toString());
        assertFalse(parse().hasTimeRange());
    }

    @Test
    void paramsRejectUnknownNamesAndSplitCommaLists() {
        Map<String, String[]> raw = new HashMap<>();
        raw.put("user", new String[]{"alice"});
        AgentApiException error = assertThrows(AgentApiException.class,
                () -> new AgentParams(raw, AgentSubmissionFilter.PARAMS));
        assertEquals(400, error.getStatus());
        assertTrue(error.getMessage().contains("user"));

        Map<String, String[]> ok = new HashMap<>();
        ok.put("username", new String[]{" a , b,,a ", "c"});
        ok.put("limit", new String[]{"999"});
        AgentParams params = new AgentParams(ok, AgentParams.names("username", "limit"));
        List<String> names = params.list("username");
        assertEquals(Arrays.asList("a", "b", "c"), names);
        assertThrows(AgentApiException.class, () -> params.intValue("limit", 100, 1, 500));
    }

    @Test
    void escapesLikeWildcards() {
        assertEquals("50\\%\\_off\\\\", AgentDataService.escapeLike("50%_off\\"));
    }

    @Test
    void normalizesDriverSpecificTypesInStatsRows() {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("first_submit_at", java.time.LocalDateTime.of(2026, 11, 1, 9, 0));
        row.put("day", java.time.LocalDate.of(2026, 11, 1));
        row.put("legacy_day", java.sql.Date.valueOf("2026-10-30"));
        row.put("accepted_submissions", new java.math.BigDecimal("3"));
        row.put("users", java.math.BigInteger.valueOf(2));
        row.put("submissions", 5L);
        Map<String, Object> normalized = AgentDataService.normalize(row, SHANGHAI);
        assertEquals("2026-11-01T09:00:00+08:00", normalized.get("first_submit_at"));
        assertEquals("2026-11-01", normalized.get("day"));
        assertEquals("2026-10-30", normalized.get("legacy_day"));
        assertEquals(3L, normalized.get("accepted_submissions"));
        assertEquals(2L, normalized.get("users"));
        assertEquals(5L, normalized.get("submissions"));

        Map<String, Object> empty = new java.util.LinkedHashMap<>();
        empty.put("accepted_submissions", null);
        assertEquals(0L, AgentDataService.normalize(empty, SHANGHAI).get("accepted_submissions"));
    }

    @Test
    void formatsTimesWithSiteOffset() {
        assertEquals("2026-11-01T19:32:10+08:00", AgentDataService.formatTime(
                Timestamp.from(instant("2026-11-01T11:32:10Z")), SHANGHAI));
    }
}
