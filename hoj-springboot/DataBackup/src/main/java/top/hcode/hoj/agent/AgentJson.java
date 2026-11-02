package top.hcode.hoj.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 数据接口使用独立的 ObjectMapper 输出，字段名与格式不受全局 Jackson 配置影响，保持接口稳定。
 */
public final class AgentJson {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentJson() {
    }

    public static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(body));
        response.getWriter().flush();
    }

    public static void writeError(HttpServletResponse response, AgentApiException error) throws IOException {
        write(response, error.getStatus(), errorBody(error.getCode(), error.getMessage()));
    }

    public static Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", code);
        detail.put("message", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", detail);
        return body;
    }

    public static String line(Object value) {
        try {
            return MAPPER.writeValueAsString(value) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
