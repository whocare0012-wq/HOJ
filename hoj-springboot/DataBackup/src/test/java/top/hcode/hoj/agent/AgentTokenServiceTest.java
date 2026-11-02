package top.hcode.hoj.agent;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTokenServiceTest {

    @Test
    void generatesUniqueHighEntropyTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = AgentTokenService.generateRawToken();
            assertTrue(token.matches("hoj_at_[A-Za-z0-9_-]{43}"), token);
            tokens.add(token);
        }
        assertEquals(200, tokens.size());
    }

    @Test
    void hashIsStableSha256Hex() {
        String hash = AgentTokenService.hash("hoj_at_example");
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, AgentTokenService.hash("hoj_at_example"));
        assertNotEquals(hash, AgentTokenService.hash("hoj_at_examplf"));
    }

    @Test
    void extractsBearerOrBareTokens() {
        assertEquals("hoj_at_abc", AgentTokenService.extractToken("Bearer hoj_at_abc"));
        assertEquals("hoj_at_abc", AgentTokenService.extractToken("bearer   hoj_at_abc "));
        assertEquals("hoj_at_abc", AgentTokenService.extractToken("hoj_at_abc"));
        assertNull(AgentTokenService.extractToken(null));
        assertNull(AgentTokenService.extractToken("Bearer eyJhbGciOiJIUzUxMiJ9.jwt"));
        assertNull(AgentTokenService.extractToken("Bearer hoj_at_"));
    }

    @Test
    void adminRowsCarryAbsoluteInstantsInSiteTimezone() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("gmtCreate", java.time.LocalDateTime.of(2026, 11, 2, 16, 36, 30));
        row.put("expiresAt", null);
        row.put("tokenName", "x");
        new AgentTokenService().toInstants(Collections.singletonList(row));
        assertEquals(java.time.Instant.parse("2026-11-02T08:36:30Z"),
                ((java.util.Date) row.get("gmtCreate")).toInstant());
        assertNull(row.get("expiresAt"));
        assertEquals("x", row.get("tokenName"));
    }

    @Test
    void createValidatesNameAndExpiry() {
        AgentTokenService service = new AgentTokenService();
        assertThrows(IllegalArgumentException.class, () -> service.create("  ", 30, "root"));
        assertThrows(IllegalArgumentException.class, () -> service.create("x", 0, "root"));
        assertThrows(IllegalArgumentException.class, () -> service.create("x", 3651, "root"));
    }

    @Test
    void authenticateRejectsEveryInvalidState() {
        assertCode("missing_token", null, null);
        assertCode("invalid_token", "Bearer hoj_at_unknown", null);
        assertCode("token_revoked", "Bearer hoj_at_x", row(1, 0, 1));
        assertCode("token_expired", "Bearer hoj_at_x", row(0, 1, 1));
        assertCode("creator_not_root", "Bearer hoj_at_x", row(0, 0, 0));
    }

    @Test
    void authenticateAcceptsActiveTokenOfRootCreator() {
        AgentTokenService service = serviceReturning(row(0, 0, 1));
        AgentTokenService.AgentPrincipal principal = service.authenticate("Bearer hoj_at_x");
        assertEquals(9L, principal.getTokenId());
        assertEquals("analysis", principal.getTokenName());
    }

    private static Map<String, Object> row(int revoked, int expired, int creatorIsRoot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 9L);
        row.put("tokenName", "analysis");
        row.put("revoked", revoked);
        row.put("expired", expired);
        row.put("creatorIsRoot", creatorIsRoot);
        return row;
    }

    private static AgentTokenService serviceReturning(Map<String, Object> row) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(java.sql.Timestamp.class),
                eq(AgentTokenService.ROOT_ROLE_ID), any(String.class)))
                .thenReturn(row == null ? Collections.emptyList() : Collections.singletonList(row));
        AgentTokenService service = new AgentTokenService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        return service;
    }

    private static void assertCode(String code, String header, Map<String, Object> row) {
        AgentApiException error = assertThrows(AgentApiException.class,
                () -> serviceReturning(row).authenticate(header));
        assertEquals(code, error.getCode());
        assertEquals("creator_not_root".equals(code) ? 403 : 401, error.getStatus());
    }
}
