package top.hcode.hoj.agent;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScopeTest {

    @Test
    void normalizesToStableOrderAndRejectsUnknownOrEmpty() {
        assertEquals("read,manage", AgentScope.normalize(Arrays.asList("MANAGE", " read ", "manage")));
        assertEquals("manage", AgentScope.normalize(Collections.singletonList("manage")));
        assertThrows(IllegalArgumentException.class, () -> AgentScope.normalize(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> AgentScope.normalize(Arrays.asList("read", "write")));
    }

    @Test
    void legacyTokensWithoutScopesAreReadOnly() {
        assertEquals(Collections.singleton("read"), AgentScope.parse(null));
        assertEquals(Collections.singleton("read"), AgentScope.parse(""));
        assertTrue(AgentScope.parse("read,manage").contains("manage"));
        assertFalse(AgentScope.parse("manage").contains("read"));
    }

    @Test
    void managementPathsRequireManageScope() {
        assertEquals("manage", AgentScope.requiredFor("/api/agent/v1/admin/problems"));
        assertEquals("manage", AgentScope.requiredFor("/api/agent/v1/admin/trainings/3/problems"));
        assertEquals("read", AgentScope.requiredFor("/api/agent/v1/submissions"));
        assertEquals("read", AgentScope.requiredFor("/api/agent/v1/admin"));
        assertNull(AgentScope.requiredFor("/api/agent/v1/ping"));
        assertTrue(AgentScope.isManagePath("/api/agent/v1/admin/meta"));
        assertFalse(AgentScope.isManagePath("/api/agent/v1/stats"));
    }
}
