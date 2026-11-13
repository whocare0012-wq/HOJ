package top.hcode.hoj.controller.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import top.hcode.hoj.agent.AgentApiException;
import top.hcode.hoj.agent.AgentDataService;
import top.hcode.hoj.agent.AgentJson;
import top.hcode.hoj.agent.AgentParams;
import top.hcode.hoj.agent.manage.AgentBody;
import top.hcode.hoj.agent.manage.AgentCaller;
import top.hcode.hoj.agent.manage.AgentMetaService;
import top.hcode.hoj.agent.manage.AgentProblemService;
import top.hcode.hoj.agent.manage.AgentTrainingService;
import top.hcode.hoj.interceptor.AgentTokenInterceptor;
import top.hcode.hoj.shiro.AccountProfile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 面向 AI agent 的题目与训练管理接口，需要带 manage 权限的令牌（见 {@link AgentTokenInterceptor}），
 * 以令牌创建者（root）的身份执行。所有写操作支持 dry_run=true：只校验并返回变更预览，不写入。
 * 接口说明见 openapi.json。
 */
@RestController
@RequestMapping("/api/agent/v1/admin")
@Slf4j(topic = "hoj")
public class AgentManageController {

    /** JSON 请求体上限（手动测试数据会放在请求体中）；更大的测试数据请用 zip 上传 */
    static final int MAX_JSON_BYTES = 64 * 1024 * 1024;

    private static final Set<String> DRY_RUN_PARAM = AgentParams.names("dry_run");
    private static final Set<String> MULTIPART_PARAMS = AgentParams.names("dry_run", "data");
    private static final Set<String> PROBLEM_LIST_PARAMS = AgentParams.names("keyword", "auth", "page", "limit");
    private static final Set<String> TEST_CASE_PARAMS = AgentParams.names("include_content", "max_chars");
    private static final Set<String> TRAINING_LIST_PARAMS = AgentParams.names("keyword", "page", "limit");

    @Resource
    private AgentProblemService problemService;

    @Resource
    private AgentTrainingService trainingService;

    @Resource
    private AgentMetaService metaService;

    @Resource
    private AgentDataService dataService;

    // ================================================================== 元数据

    @GetMapping("/meta")
    public void meta(HttpServletRequest request, HttpServletResponse response) throws IOException {
        new AgentParams(request.getParameterMap(), AgentParams.names());
        AgentJson.write(response, 200, metaService.meta());
    }

    // ================================================================== 题目

    @GetMapping("/problems")
    public void listProblems(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AgentParams params = new AgentParams(request.getParameterMap(), PROBLEM_LIST_PARAMS);
        String auth = params.choice("auth", null, "public", "private", "contest");
        Map<String, Object> body = problemService.list(keyword(params), auth,
                params.intValue("page", 1, 1, 100_000), params.intValue("limit", 50, 1, 200), zone());
        countResult(request, body);
        AgentJson.write(response, 200, body);
    }

    @GetMapping("/problems/{problemId}")
    public void getProblem(@PathVariable String problemId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        new AgentParams(request.getParameterMap(), AgentParams.names());
        writeData(response, problemService.get(problemId, zone()));
    }

    @PostMapping("/problems")
    public void createProblem(HttpServletRequest request, HttpServletResponse response) throws IOException {
        RequestInput input = readInput(request);
        AgentBody body = new AgentBody(input.json, "请求体", AgentProblemService.CREATE_FIELDS);
        AgentJson.write(response, 200, problemService.create(body, input.file, caller(request, body), zone()));
    }

    @PatchMapping("/problems/{problemId}")
    public void updateProblem(@PathVariable String problemId, HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        RequestInput input = readInput(request);
        if (input.file != null) {
            throw AgentApiException.badRequest("修改题目不接受文件；替换测试数据请用 PUT /api/agent/v1/admin/problems/{problem_id}/testcases");
        }
        AgentBody body = new AgentBody(input.json, "请求体", AgentProblemService.UPDATE_FIELDS);
        AgentJson.write(response, 200, problemService.update(problemId, body, caller(request, body), zone()));
    }

    @GetMapping("/problems/{problemId}/testcases")
    public void listTestCases(@PathVariable String problemId, HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        AgentParams params = new AgentParams(request.getParameterMap(), TEST_CASE_PARAMS);
        Map<String, Object> body = problemService.listTestCases(problemId, params.bool("include_content", false),
                params.intValue("max_chars", 2000, 1, 1_000_000));
        countResult(request, body);
        AgentJson.write(response, 200, body);
    }

    @PutMapping("/problems/{problemId}/testcases")
    public void replaceTestCases(@PathVariable String problemId, HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {
        RequestInput input = readInput(request);
        AgentBody body = new AgentBody(input.json, "请求体", AgentProblemService.TEST_CASE_BODY_FIELDS);
        AgentJson.write(response, 200,
                problemService.replaceTestCases(problemId, body, input.file, caller(request, body), zone()));
    }

    // ================================================================== 训练

    @GetMapping("/trainings")
    public void listTrainings(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AgentParams params = new AgentParams(request.getParameterMap(), TRAINING_LIST_PARAMS);
        Map<String, Object> body = trainingService.list(keyword(params),
                params.intValue("page", 1, 1, 100_000), params.intValue("limit", 50, 1, 200), zone());
        countResult(request, body);
        AgentJson.write(response, 200, body);
    }

    @GetMapping("/trainings/{tid}")
    public void getTraining(@PathVariable String tid, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        new AgentParams(request.getParameterMap(), AgentParams.names());
        writeData(response, trainingService.get(trainingId(tid), zone()));
    }

    @PostMapping("/trainings")
    public void createTraining(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AgentBody body = jsonBody(request, AgentTrainingService.CREATE_FIELDS);
        AgentJson.write(response, 200, trainingService.create(body, caller(request, body), zone()));
    }

    @PatchMapping("/trainings/{tid}")
    public void updateTraining(@PathVariable String tid, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        long id = trainingId(tid);
        AgentBody body = jsonBody(request, AgentTrainingService.UPDATE_FIELDS);
        AgentJson.write(response, 200, trainingService.update(id, body, caller(request, body), zone()));
    }

    @PostMapping("/trainings/{tid}/problems")
    public void addTrainingProblems(@PathVariable String tid, HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        long id = trainingId(tid);
        AgentBody body = jsonBody(request, AgentTrainingService.ADD_PROBLEMS_FIELDS);
        AgentJson.write(response, 200, trainingService.addProblems(id, body, caller(request, body), zone()));
    }

    @PutMapping("/trainings/{tid}/problems/order")
    public void reorderTrainingProblems(@PathVariable String tid, HttpServletRequest request,
                                        HttpServletResponse response) throws IOException {
        long id = trainingId(tid);
        AgentBody body = jsonBody(request, AgentTrainingService.REORDER_FIELDS);
        AgentJson.write(response, 200, trainingService.reorder(id, body, caller(request, body), zone()));
    }

    @PatchMapping("/trainings/{tid}/problems/{problemId}")
    public void updateTrainingProblem(@PathVariable String tid, @PathVariable String problemId,
                                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        long id = trainingId(tid);
        AgentBody body = jsonBody(request, AgentTrainingService.PROBLEM_PATCH_FIELDS);
        AgentJson.write(response, 200,
                trainingService.updateProblem(id, problemId, body, caller(request, body), zone()));
    }

    @DeleteMapping("/trainings/{tid}/problems/{problemId}")
    public void removeTrainingProblem(@PathVariable String tid, @PathVariable String problemId,
                                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        long id = trainingId(tid);
        AgentBody body = jsonBody(request, AgentTrainingService.REMOVE_FIELDS);
        AgentJson.write(response, 200, trainingService.removeProblem(id, problemId, caller(request, body), zone()));
    }

    // ================================================================== 错误处理

    @ExceptionHandler(AgentApiException.class)
    public void handleAgentError(AgentApiException error, HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            AgentJson.writeError(response, error);
        }
    }

    @ExceptionHandler(Exception.class)
    public void handleUnexpected(Exception error, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.error("[Agent API] {} {} failed", request.getMethod(), request.getRequestURI(), error);
        if (!response.isCommitted()) {
            AgentJson.write(response, 500, AgentJson.errorBody("internal_error",
                    "服务器内部错误，请稍后重试；写操作可能已部分完成，请重新获取对象确认当前状态"));
        }
    }

    // ================================================================== 内部

    private static final class RequestInput {
        final JsonNode json;
        final MultipartFile file;

        RequestInput(JsonNode json, MultipartFile file) {
            this.json = json;
            this.file = file;
        }
    }

    /**
     * JSON 请求直接读请求体；multipart 请求中 JSON 放在 data 字段、zip 放在 file 字段。
     */
    private RequestInput readInput(HttpServletRequest request) throws IOException {
        if (request instanceof MultipartHttpServletRequest) {
            new AgentParams(request.getParameterMap(), MULTIPART_PARAMS);
            MultipartHttpServletRequest multipart = (MultipartHttpServletRequest) request;
            for (String name : multipart.getFileMap().keySet()) {
                if (!"file".equals(name)) {
                    throw AgentApiException.badRequest("未知的文件字段 " + name + "；测试数据 zip 请放在 file 字段");
                }
            }
            String data = multipart.getParameter("data");
            JsonNode json = data == null || data.trim().isEmpty() ? null : parseJson(data);
            json = mergeDryRun(json, request);
            MultipartFile file = multipart.getFile("file");
            return new RequestInput(json, file == null || file.isEmpty() ? null : file);
        }
        new AgentParams(request.getParameterMap(), DRY_RUN_PARAM);
        return new RequestInput(mergeDryRun(readJson(request), request), null);
    }

    private AgentBody jsonBody(HttpServletRequest request, Set<String> fields) throws IOException {
        if (request instanceof MultipartHttpServletRequest) {
            throw AgentApiException.badRequest("该接口只接受 application/json 请求体");
        }
        new AgentParams(request.getParameterMap(), DRY_RUN_PARAM);
        return new AgentBody(mergeDryRun(readJson(request), request), "请求体", fields);
    }

    private static JsonNode readJson(HttpServletRequest request) throws IOException {
        int length = request.getContentLength();
        if (length > MAX_JSON_BYTES) {
            throw new AgentApiException(413, "payload_too_large",
                    "请求体超过 " + (MAX_JSON_BYTES / 1024 / 1024) + "MB；大量测试数据请打包成 zip 以 multipart 上传");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(length, 1024));
        try (InputStream input = request.getInputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(chunk)) != -1) {
                total += read;
                if (total > MAX_JSON_BYTES) {
                    throw new AgentApiException(413, "payload_too_large",
                            "请求体超过 " + (MAX_JSON_BYTES / 1024 / 1024) + "MB；大量测试数据请打包成 zip 以 multipart 上传");
                }
                buffer.write(chunk, 0, read);
            }
        }
        String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        return text.trim().isEmpty() ? null : parseJson(text);
    }

    private static JsonNode parseJson(String text) {
        try {
            return AgentJson.MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new AgentApiException(400, "invalid_json", "请求体不是合法的 JSON：" + e.getOriginalMessage());
        }
    }

    /** dry_run 既可以作为查询参数，也可以写在请求体中；任意一处为 true 即为演练 */
    private static JsonNode mergeDryRun(JsonNode json, HttpServletRequest request) {
        String[] values = request.getParameterValues("dry_run");
        if (values == null) {
            return json;
        }
        boolean dryRun = new AgentParams(java.util.Collections.singletonMap("dry_run", values),
                DRY_RUN_PARAM).bool("dry_run", false);
        com.fasterxml.jackson.databind.node.ObjectNode node;
        if (json == null || json.isNull()) {
            node = AgentJson.MAPPER.createObjectNode();
        } else if (json.isObject()) {
            node = (com.fasterxml.jackson.databind.node.ObjectNode) json;
        } else {
            return json;
        }
        JsonNode existing = node.get("dry_run");
        boolean fromBody = existing != null && existing.isBoolean() && existing.asBoolean();
        node.put("dry_run", dryRun || fromBody);
        return node;
    }

    private static AgentCaller caller(HttpServletRequest request, AgentBody body) {
        Object tokenId = request.getAttribute(AgentTokenInterceptor.TOKEN_ID_ATTR);
        Object operator = request.getAttribute(AgentTokenInterceptor.OPERATOR_ATTR);
        if (!(tokenId instanceof Long) || !(operator instanceof AccountProfile)) {
            throw new AgentApiException(401, "missing_token", "缺少有效的管理令牌");
        }
        Boolean dryRun = body.bool("dry_run");
        return new AgentCaller((Long) tokenId, (AccountProfile) operator, Boolean.TRUE.equals(dryRun));
    }

    private static String keyword(AgentParams params) {
        String keyword = params.single("keyword");
        if (keyword != null && keyword.length() > 100) {
            throw AgentApiException.badRequest("keyword 不超过 100 个字符");
        }
        return keyword;
    }

    private static long trainingId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw AgentApiException.badRequest("训练 ID 必须是整数: " + value);
        }
    }

    private static void writeData(HttpServletResponse response, Map<String, Object> data) throws IOException {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("data", data);
        AgentJson.write(response, 200, body);
    }

    private static void countResult(HttpServletRequest request, Map<String, Object> body) {
        Object data = body.get("data");
        if (data instanceof List) {
            request.setAttribute(AgentTokenInterceptor.RESULT_COUNT_ATTR, ((List<?>) data).size());
        }
    }

    private ZoneId zone() {
        return dataService.zone();
    }
}
