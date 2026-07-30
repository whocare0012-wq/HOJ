package top.hcode.hoj.judge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SandboxRunTest {

    @Test
    void usesLegacyLocalAddressWhenNoOverrideIsConfigured() {
        assertEquals("http://localhost:5050", SandboxRun.normalizeSandboxBaseUrl(null));
        assertEquals("http://localhost:5050", SandboxRun.normalizeSandboxBaseUrl("   "));
    }

    @Test
    void trimsWhitespaceAndTrailingSlashesFromOverride() {
        assertEquals(
                "http://127.0.0.1:15050",
                SandboxRun.normalizeSandboxBaseUrl("  http://127.0.0.1:15050///  ")
        );
    }
}
