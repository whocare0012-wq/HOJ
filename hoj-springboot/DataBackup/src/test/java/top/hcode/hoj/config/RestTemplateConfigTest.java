package top.hcode.hoj.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestTemplateConfigTest {

    @Test
    void givesAiRequestsFiveMinutesToReturnContent() {
        RestTemplate restTemplate = new RestTemplateConfig().aiAssistantRestTemplate();
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();

        assertEquals(
                300000,
                ReflectionTestUtils.getField(requestFactory, "readTimeout"));
    }
}
