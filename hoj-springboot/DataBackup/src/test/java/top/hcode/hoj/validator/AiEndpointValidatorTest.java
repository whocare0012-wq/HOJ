package top.hcode.hoj.validator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiEndpointValidatorTest {

    private final AiEndpointValidator validator =
            new AiEndpointValidator(false, "");

    @Test
    void acceptsPublicHttpsEndpointAndRemovesTrailingSlash() {
        assertEquals(
                "https://8.8.8.8/v1",
                validator.validateAndNormalize("https://8.8.8.8/v1/"));
    }

    @Test
    void rejectsPlainHttpByDefault() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateAndNormalize("http://8.8.8.8/v1"));
    }

    @Test
    void rejectsLoopbackAndCloudMetadataNetworks() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateAndNormalize("https://127.0.0.1/v1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateAndNormalize("https://169.254.169.254/latest"));
    }

    @Test
    void rejectsUserInfoAndQueryParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateAndNormalize("https://token@8.8.8.8/v1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateAndNormalize("https://8.8.8.8/v1?target=internal"));
    }

    @Test
    void honorsAnExplicitHostAllowList() {
        AiEndpointValidator allowListed =
                new AiEndpointValidator(false, "8.8.8.8");
        assertEquals(
                "https://8.8.8.8/v1",
                allowListed.validateAndNormalize("https://8.8.8.8/v1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> allowListed.validateAndNormalize("https://1.1.1.1/v1"));
    }
}
