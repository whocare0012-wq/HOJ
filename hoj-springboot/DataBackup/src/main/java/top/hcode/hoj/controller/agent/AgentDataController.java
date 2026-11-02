package top.hcode.hoj.controller.agent;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.agent.AgentApiException;
import top.hcode.hoj.agent.AgentDataService;
import top.hcode.hoj.agent.AgentJson;
import top.hcode.hoj.agent.AgentParams;
import top.hcode.hoj.agent.AgentSubmissionFilter;
import top.hcode.hoj.interceptor.AgentTokenInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 面向 AI agent 的只读数据接口。鉴权见 {@link AgentTokenInterceptor}，接口说明见 openapi.json。
 */
@RestController
@RequestMapping("/api/agent/v1")
@Slf4j(topic = "hoj")
public class AgentDataController {

    static final int MAX_LIMIT_WITH_CODE = 200;
    static final int MAX_LIMIT_WITHOUT_CODE = 500;

    private static final Set<String> SUBMISSION_PARAMS = with(AgentSubmissionFilter.PARAMS,
            "include_code", "limit", "cursor", "order", "format");
    private static final Set<String> STATS_PARAMS = with(AgentSubmissionFilter.PARAMS, "group_by", "limit");
    private static final Set<String> STUDENT_PARAMS = AgentParams.names("keyword", "group_id", "limit", "cursor");

    @Resource
    private AgentDataService dataService;

    @GetMapping("/ping")
    public void ping(HttpServletRequest request, HttpServletResponse response) throws IOException {
        new AgentParams(request.getParameterMap(), AgentParams.names());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("api_version", "v1");
        body.put("token_name", request.getAttribute(AgentTokenInterceptor.TOKEN_NAME_ATTR));
        body.put("timezone", dataService.zone().getId());
        body.put("server_time", ZonedDateTime.now(dataService.zone()).withNano(0).toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        AgentJson.write(response, 200, body);
    }

    @GetMapping("/submissions")
    public void listSubmissions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AgentParams params = new AgentParams(request.getParameterMap(), SUBMISSION_PARAMS);
        AgentSubmissionFilter filter = AgentSubmissionFilter.parse(params, dataService.zone());
        boolean includeCode = params.bool("include_code", true);
        boolean desc = "desc".equals(params.choice("order", "asc", "asc", "desc"));
        Long cursor = params.longValue("cursor");
        String format = params.choice("format", "json", "json", "ndjson");

        if ("ndjson".equals(format)) {
            Integer maxRows = params.single("limit") == null ? null
                    : params.intValue("limit", 0, 1, Integer.MAX_VALUE);
            response.setStatus(200);
            response.setContentType("application/x-ndjson;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            PrintWriter writer = response.getWriter();
            int[] lines = {0};
            int count = dataService.streamSubmissions(filter, includeCode, cursor, desc, maxRows, row -> {
                writer.write(AgentJson.line(row));
                if (++lines[0] % AgentDataService.STREAM_BATCH_SIZE == 0) {
                    writer.flush();
                }
            });
            writer.flush();
            request.setAttribute(AgentTokenInterceptor.RESULT_COUNT_ATTR, count);
            return;
        }

        int maxLimit = includeCode ? MAX_LIMIT_WITH_CODE : MAX_LIMIT_WITHOUT_CODE;
        int limit = params.intValue("limit", 100, 1, maxLimit);
        AgentDataService.SubmissionPage page = dataService.listSubmissions(filter, includeCode, limit, cursor, desc);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", page.getRows());
        body.put("count", page.getRows().size());
        body.put("has_more", page.isHasMore());
        body.put("next_cursor", page.getNextCursor() == null ? null : String.valueOf(page.getNextCursor()));
        request.setAttribute(AgentTokenInterceptor.RESULT_COUNT_ATTR, page.getRows().size());
        AgentJson.write(response, 200, body);
    }

    @GetMapping("/submissions/{submitId}")
    public void getSubmission(@PathVariable String submitId, HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        new AgentParams(request.getParameterMap(), AgentParams.names());
        long id;
        try {
            id = Long.parseLong(submitId);
        } catch (NumberFormatException e) {
            throw AgentApiException.badRequest("提交 ID 必须是整数: " + submitId);
        }
        Map<String, Object> submission = dataService.getSubmission(id);
        request.setAttribute(AgentTokenInterceptor.RESULT_COUNT_ATTR, 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", submission);
        AgentJson.write(response, 200, body);
    }

    @GetMapping("/students")
    public void listStudents(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AgentParams params = new AgentParams(request.getParameterMap(), STUDENT_PARAMS);
        String keyword = params.single("keyword");
        if (keyword != null && keyword.length() > 100) {
            throw AgentApiException.badRequest("keyword 不超过 100 个字符");
        }
        Map<String, Object> body = dataService.listStudents(keyword, params.longValue("group_id"),
                params.intValue("limit", 100, 1, 500), params.single("cursor"));
        request.setAttribute(AgentTokenInterceptor.RESULT_COUNT_ATTR, ((java.util.List<?>) body.get("data")).size());
        AgentJson.write(response, 200, body);
    }

    @GetMapping("/stats")
    public void stats(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AgentParams params = new AgentParams(request.getParameterMap(), STATS_PARAMS);
        AgentSubmissionFilter filter = AgentSubmissionFilter.parse(params, dataService.zone());
        String groupBy = params.choice("group_by", "user", "user", "problem", "day", "status", "language");
        Map<String, Object> body = dataService.stats(filter, groupBy, params.intValue("limit", 200, 1, 5000));
        request.setAttribute(AgentTokenInterceptor.RESULT_COUNT_ATTR, ((java.util.List<?>) body.get("rows")).size());
        AgentJson.write(response, 200, body);
    }

    /** OpenAPI 文档，无需令牌，便于各类 agent 平台直接导入 */
    @GetMapping("/openapi.json")
    public void openApi(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ObjectNode spec;
        try (InputStream input = new ClassPathResource("agent-api/openapi.json").getInputStream()) {
            spec = (ObjectNode) AgentJson.MAPPER.readTree(input);
        }
        ArrayNode servers = spec.putArray("servers");
        servers.addObject().put("url", baseUrl(request));
        AgentJson.write(response, 200, spec);
    }

    @ExceptionHandler(AgentApiException.class)
    public void handleAgentError(AgentApiException error, HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            AgentJson.writeError(response, error);
        }
    }

    @ExceptionHandler(Exception.class)
    public void handleUnexpected(Exception error, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.error("[Agent API] {} failed", request.getRequestURI(), error);
        if (!response.isCommitted()) {
            AgentJson.write(response, 500, AgentJson.errorBody("internal_error", "服务器内部错误，请稍后重试"));
        }
    }

    static String baseUrl(HttpServletRequest request) {
        String proto = firstHeaderValue(request, "X-Forwarded-Proto");
        String host = firstHeaderValue(request, "X-Forwarded-Host");
        if (proto == null) {
            proto = request.getScheme();
        }
        if (host == null) {
            host = request.getHeader("Host");
        }
        if (host == null) {
            int port = request.getServerPort();
            boolean defaultPort = ("http".equals(proto) && port == 80) || ("https".equals(proto) && port == 443);
            host = request.getServerName() + (defaultPort ? "" : ":" + port);
        }
        return proto + "://" + host;
    }

    private static String firstHeaderValue(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.split(",")[0].trim();
    }

    private static Set<String> with(Set<String> base, String... extra) {
        Set<String> result = new LinkedHashSet<>(base);
        for (String name : extra) {
            result.add(name);
        }
        return result;
    }
}
