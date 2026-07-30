package top.hcode.hoj.common.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonResultJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesJudgeServerResponse() throws Exception {
        CommonResult<?> result = objectMapper.readValue(
                "{\"status\":200,\"data\":{\"accepted\":true},\"msg\":\"success\"}",
                CommonResult.class);

        assertEquals(200, result.getStatus());
        assertEquals(true, ((Map<?, ?>) result.getData()).get("accepted"));
        assertEquals("success", result.getMsg());
    }
}
