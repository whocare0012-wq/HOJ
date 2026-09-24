package top.hcode.hoj.crawler.problem;

import org.junit.jupiter.api.Test;
import top.hcode.hoj.pojo.entity.problem.Problem;
import top.hcode.hoj.pojo.entity.problem.ProblemCase;
import top.hcode.hoj.utils.Constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtCoderProblemStrategyTest {

    private final AtCoderProblemStrategy strategy = new AtCoderProblemStrategy();

    @Test
    void parsesCurrentMiBMemoryLimit() {
        ProblemStrategy.RemoteProblemInfo info = strategy.parseProblemInfo(
                "dp_a", "admin", problemPage("2", "1024", "MiB"));

        Problem problem = info.getProblem();
        assertEquals("AC-dp_a", problem.getProblemId());
        assertEquals("Frog 1", problem.getTitle());
        assertEquals(2000, problem.getTimeLimit());
        assertEquals(1024, problem.getMemoryLimit());
        assertEquals(128, problem.getStackLimit());
        assertEquals("admin", problem.getAuthor());
        assertTrue(problem.getDescription().contains("Jump between stones"));
        assertTrue(problem.getInput().contains("N"));
        assertTrue(problem.getOutput().contains("minimum cost"));
        assertEquals("<input>4\n10 30 40 20</input><output>30</output>", problem.getExamples());
        assertFalse(problem.getIsRemote());
        assertFalse(problem.getIsUploadCase());
        assertEquals(Constants.JudgeMode.DEFAULT.getMode(), problem.getJudgeMode());
        assertEquals(Constants.JudgeCaseMode.DEFAULT.getMode(), problem.getJudgeCaseMode());

        assertEquals(1, info.getProblemCaseList().size());
        ProblemCase problemCase = info.getProblemCaseList().get(0);
        assertEquals("4\n10 30 40 20", problemCase.getInput());
        assertEquals("30", problemCase.getOutput());
        assertEquals(0, problemCase.getStatus());
        assertEquals(1, problemCase.getGroupNum());
    }

    @Test
    void keepsSupportingLegacyMBMemoryLimitAndFractionalSeconds() {
        Problem problem = strategy.parseProblemInfo(
                "ABC110_A", "admin", problemPage("1.5", "256", "MB"))
                .getProblem();

        assertEquals("AC-abc110_a", problem.getProblemId());
        assertEquals(1500, problem.getTimeLimit());
        assertEquals(256, problem.getMemoryLimit());
    }

    @Test
    void rejectsPrefixesTitlesAndMissingTaskSuffixes() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.normalizeProblemId("AtCoder-dp_a"));
        assertThrows(IllegalArgumentException.class,
                () -> strategy.normalizeProblemId("A - Frog 1"));
        assertThrows(IllegalArgumentException.class,
                () -> strategy.normalizeProblemId("dp"));
    }

    @Test
    void reportsAnActionableErrorWhenTheLimitLayoutIsUnsupported() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> strategy.parseProblemInfo(
                        "dp_a", "admin", problemPage("2", "1024", "GiB")));

        assertTrue(exception.getMessage().contains("time or memory limit"));
        assertTrue(exception.getMessage().contains("dp_a"));
    }

    @Test
    void rejectsStatementsWithoutCompleteSamplePairs() {
        String page = problemPage("2", "1024", "MiB")
                .replace(part("Sample Output 1", "<pre>30</pre>"), "");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> strategy.parseProblemInfo("dp_a", "admin", page));

        assertTrue(exception.getMessage().contains("sample input/output pairs"));
    }

    private String problemPage(String seconds, String memory, String memoryUnit) {
        return "<html><head><title>A - Frog 1</title></head><body>"
                + "<p>Time Limit: " + seconds + " sec / Memory Limit: " + memory + " " + memoryUnit + "</p>"
                + "<div id=\"task-statement\">"
                + part("Problem Statement", "<p>Jump between stones.</p>")
                + part("Input", "<p><var>N</var></p><pre>input format</pre>")
                + part("Constraints", "<ul><li><var>N</var> is positive.</li></ul>")
                + part("Output", "<p>Print the minimum cost.</p>")
                + part("Sample Input 1", "<pre>4\n10 30 40 20</pre>")
                + part("Sample Output 1", "<pre>30</pre>")
                + "</div></body></html>";
    }

    private String part(String heading, String content) {
        return "<div class=\"part\"><section><h3>" + heading + "</h3>"
                + content + "</section></div>";
    }
}
