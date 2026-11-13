package top.hcode.hoj.agent;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentAuditPageTest {
    @Test
    void emptyHistoryHasOneValidEmptyPage() {
        AgentAuditPage page = new AgentAuditPage(999, 20, 0);
        Map<String, Object> result = page.result(Collections.emptyList());
        assertEquals(1, result.get("currentPage"));
        assertEquals(0L, result.get("total"));
        assertEquals(0L, page.getOffset());
    }

    @Test
    void pageOutsideAvailableHistoryFallsBackToLastPage() {
        AgentAuditPage page = new AgentAuditPage(8, 20, 41);
        assertEquals(3, page.result(Collections.emptyList()).get("currentPage"));
        assertEquals(40L, page.getOffset());
    }

    @Test
    void pageSizeAndPageAreBoundedBeforeQuerying() {
        AgentAuditPage page = new AgentAuditPage(-1, 10000, 500);
        assertEquals(200, page.getPageSize());
        assertEquals(0L, page.getOffset());
        assertEquals(1, new AgentAuditPage(1, 0, 50).getPageSize());
    }

    @Test
    void largeOffsetsDoNotOverflowIntegerArithmetic() {
        AgentAuditPage page = new AgentAuditPage(Integer.MAX_VALUE, 200, Long.MAX_VALUE);
        assertEquals(429496729200L, page.getOffset());
    }
}
