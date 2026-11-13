package top.hcode.hoj.agent.manage;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import top.hcode.hoj.agent.AgentApiException;
import top.hcode.hoj.agent.AgentJson;
import top.hcode.hoj.pojo.entity.problem.Problem;
import top.hcode.hoj.pojo.entity.problem.ProblemCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentViewsTest {

    @Test
    void scoresAddUpToOneHundredLikeTheAdminPage() {
        assertEquals(Arrays.toString(new int[]{33, 33, 34}), Arrays.toString(AgentViews.distributeScores(3)));
        assertEquals(Arrays.toString(new int[]{100}), Arrays.toString(AgentViews.distributeScores(1)));
        for (int count : new int[]{1, 3, 7, 99, 100, 101, 250}) {
            assertEquals(100, Arrays.stream(AgentViews.distributeScores(count)).sum(), "count=" + count);
        }
        assertEquals(0, AgentViews.distributeScores(0).length);
    }

    @Test
    void examplesRoundTripThroughTheStoredFormat() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("input", "1 2\n");
        first.put("output", "3\n");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("input", "");
        second.put("output", "0");
        String stored = AgentViews.formatExamples(Arrays.asList(first, second));
        assertEquals("<input>1 2\n</input><output>3\n</output><input></input><output>0</output>", stored);
        assertEquals(Arrays.asList(first, second), AgentViews.parseExamples(stored));
        assertEquals("", AgentViews.formatExamples(Collections.emptyList()));
        assertTrue(AgentViews.parseExamples(null).isEmpty());

        Map<String, String> broken = new LinkedHashMap<>();
        broken.put("input", "a</input>");
        assertThrows(AgentApiException.class, () -> AgentViews.formatExamples(Collections.singletonList(broken)));
    }

    @Test
    void authAndTypeAcceptOnlyEditableValues() {
        assertEquals(1, AgentViews.problemAuthValue("Public"));
        assertEquals(2, AgentViews.problemAuthValue("private"));
        assertThrows(AgentApiException.class, () -> AgentViews.problemAuthValue("contest"));
        assertEquals("contest", AgentViews.problemAuth(3));
        assertEquals(1, AgentViews.problemTypeValue("oi"));
        assertThrows(AgentApiException.class, () -> AgentViews.problemTypeValue("IOI"));
        assertEquals("ACM", AgentViews.problemType(0));
        assertNull(AgentViews.problemType(null));
    }

    @Test
    void diffListsOnlyChangedFields() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("title", "A+B");
        before.put("time_limit", 1000);
        before.put("tags", Collections.singletonList("入门"));
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("time_limit", 2000);
        after.put("tags", Arrays.asList("入门", "模拟"));
        Map<String, Object> diff = AgentViews.diff(before, after);
        assertEquals(Arrays.asList("time_limit", "tags"), Arrays.asList(diff.keySet().toArray()));
        assertEquals(1000, ((Map<?, ?>) diff.get("time_limit")).get("before"));
        assertEquals(2000, ((Map<?, ?>) diff.get("time_limit")).get("after"));
        assertTrue(AgentViews.diff(before, new LinkedHashMap<>(before)).isEmpty());
    }

    @Test
    void oiScoresMustBeAllOrNothing() {
        Problem oi = new Problem().setType(1).setJudgeCaseMode("subtask_lowest");
        List<ProblemCase> none = Arrays.asList(new ProblemCase(), new ProblemCase(), new ProblemCase());
        AgentProblemService.applyScores(none, oi);
        assertEquals(100, none.stream().mapToInt(ProblemCase::getScore).sum());
        assertTrue(none.stream().allMatch(c -> Integer.valueOf(1).equals(c.getGroupNum())));

        List<ProblemCase> partial = Arrays.asList(new ProblemCase().setScore(50), new ProblemCase());
        assertThrows(AgentApiException.class, () -> AgentProblemService.applyScores(partial, oi));

        Problem acm = new Problem().setType(0).setJudgeCaseMode("default");
        List<ProblemCase> acmPartial = Arrays.asList(new ProblemCase().setScore(50), new ProblemCase());
        AgentProblemService.applyScores(acmPartial, acm);
        assertEquals(100, acmPartial.stream().mapToInt(ProblemCase::getScore).sum());
        assertNull(acmPartial.get(0).getGroupNum());
    }

    @Test
    void bodyRejectsUnknownFieldsAndWrongTypes() throws Exception {
        JsonNode node = AgentJson.MAPPER.readTree("{\"title\":\"x\",\"tittle\":\"typo\"}");
        AgentApiException unknown = assertThrows(AgentApiException.class,
                () -> new AgentBody(node, "请求体", AgentProblemService.UPDATE_FIELDS));
        assertTrue(unknown.getMessage().contains("tittle"));

        AgentBody body = new AgentBody(AgentJson.MAPPER.readTree(
                "{\"time_limit\":\"1000\",\"tags\":[\" dp \",\"dp\",\"\"],\"dry_run\":true}"),
                "请求体", AgentProblemService.UPDATE_FIELDS);
        assertThrows(AgentApiException.class, () -> body.integer("time_limit", 1, 10));
        assertEquals(Collections.singletonList("dp"), body.stringList("tags", 10));
        assertEquals(Boolean.TRUE, body.bool("dry_run"));
        assertNull(body.string("title"));

        AgentApiException notObject = assertThrows(AgentApiException.class,
                () -> new AgentBody(AgentJson.MAPPER.readTree("[1]"), "请求体", AgentProblemService.UPDATE_FIELDS));
        assertEquals(400, notObject.getStatus());
    }

    @Test
    void versionMismatchIsAConflict() {
        java.util.Date modified = new java.util.Date(1_700_000_000_000L);
        AgentProblemService.checkVersion(null, modified, "题目");
        AgentProblemService.checkVersion(1_700_000_000_000L, modified, "题目");
        AgentApiException conflict = assertThrows(AgentApiException.class,
                () -> AgentProblemService.checkVersion(1L, modified, "题目"));
        assertEquals(409, conflict.getStatus());
        assertEquals("version_conflict", conflict.getCode());
    }
}
