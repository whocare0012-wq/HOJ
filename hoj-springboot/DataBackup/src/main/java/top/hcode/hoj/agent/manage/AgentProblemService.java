package top.hcode.hoj.agent.manage;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.hcode.hoj.agent.AgentApiException;
import top.hcode.hoj.agent.AgentJson;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.dao.problem.CodeTemplateEntityService;
import top.hcode.hoj.dao.problem.LanguageEntityService;
import top.hcode.hoj.dao.problem.ProblemCaseEntityService;
import top.hcode.hoj.dao.problem.ProblemEntityService;
import top.hcode.hoj.dao.problem.ProblemLanguageEntityService;
import top.hcode.hoj.dao.problem.ProblemTagEntityService;
import top.hcode.hoj.dao.problem.TagEntityService;
import top.hcode.hoj.manager.admin.problem.AdminProblemManager;
import top.hcode.hoj.manager.file.TestCaseManager;
import top.hcode.hoj.pojo.dto.CompileDTO;
import top.hcode.hoj.pojo.dto.ProblemDTO;
import top.hcode.hoj.pojo.entity.problem.CodeTemplate;
import top.hcode.hoj.pojo.entity.problem.Language;
import top.hcode.hoj.pojo.entity.problem.Problem;
import top.hcode.hoj.pojo.entity.problem.ProblemCase;
import top.hcode.hoj.pojo.entity.problem.ProblemLanguage;
import top.hcode.hoj.pojo.entity.problem.ProblemTag;
import top.hcode.hoj.pojo.entity.problem.Tag;
import top.hcode.hoj.utils.Constants;
import top.hcode.hoj.validator.ProblemValidator;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目管理：查询、新增、修改与替换测试数据。写操作全部委托给后台 {@link AdminProblemManager} /
 * {@link ProblemEntityService}，校验、落库与测试数据初始化逻辑与后台页面完全一致。
 * <p>
 * 只能管理本站题库中的题目：远程 OJ 题目与团队题目只读。接口不提供删除，下线题目请把 auth 设为 private。
 */
@Service
@Slf4j(topic = "hoj")
public class AgentProblemService {

    static final String OJ = "ME";
    static final int MAX_TEST_CASES = 1000;

    static final Set<String> COMMON_FIELDS = AgentBody.fields(
            "problem_id", "title", "description", "input", "output", "examples", "hint", "source",
            "difficulty", "auth", "time_limit", "memory_limit", "stack_limit",
            "judge_mode", "judge_case_mode", "spj_language", "spj_code",
            "code_share", "remove_end_blank", "open_case_result",
            "file_io", "io_read_file_name", "io_write_file_name",
            "tags", "languages", "code_templates");
    public static final Set<String> CREATE_FIELDS = AgentBody.fields(COMMON_FIELDS, "type", "test_cases", "case_scores", "dry_run");
    public static final Set<String> UPDATE_FIELDS = AgentBody.fields(COMMON_FIELDS, "expected_version", "dry_run");
    public static final Set<String> TEST_CASE_BODY_FIELDS = AgentBody.fields("test_cases", "case_scores", "expected_version", "dry_run");
    static final Set<String> TEST_CASE_FIELDS = AgentBody.fields("input", "output", "score", "group_num");
    static final Set<String> CASE_SCORE_FIELDS = AgentBody.fields("input", "score", "group_num");
    static final Set<String> EXAMPLE_FIELDS = AgentBody.fields("input", "output");
    static final Set<String> TEMPLATE_FIELDS = AgentBody.fields("language", "code");

    /** 参与 diff 的字段（即可修改的字段） */
    private static final List<String> EDITABLE_VIEW_KEYS = java.util.Arrays.asList(
            "problem_id", "title", "type", "auth", "difficulty", "source", "time_limit", "memory_limit",
            "stack_limit", "judge_mode", "judge_case_mode", "spj_language", "spj_code", "description", "input",
            "output", "examples", "hint", "tags", "languages", "code_templates", "code_share",
            "remove_end_blank", "open_case_result", "file_io", "io_read_file_name", "io_write_file_name");

    @Resource
    private ProblemEntityService problemEntityService;
    @Resource
    private ProblemCaseEntityService problemCaseEntityService;
    @Resource
    private ProblemTagEntityService problemTagEntityService;
    @Resource
    private TagEntityService tagEntityService;
    @Resource
    private LanguageEntityService languageEntityService;
    @Resource
    private ProblemLanguageEntityService problemLanguageEntityService;
    @Resource
    private CodeTemplateEntityService codeTemplateEntityService;
    @Resource
    private AdminProblemManager adminProblemManager;
    @Resource
    private TestCaseManager testCaseManager;
    @Resource
    private ProblemValidator problemValidator;
    @Resource
    private AgentChangeLogService changeLogService;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    // ================================================================== 查询

    public Map<String, Object> list(String keyword, String auth, int page, int limit, ZoneId zone) {
        QueryWrapper<Problem> query = new QueryWrapper<>();
        query.select("id", "problem_id", "title", "author", "type", "auth", "difficulty", "judge_mode",
                "is_remote", "source", "gmt_create", "gmt_modified");
        query.eq("is_group", false);
        if (auth != null) {
            query.eq("auth", "contest".equals(auth) ? 3 : AgentViews.problemAuthValue(auth));
        }
        if (keyword != null) {
            query.and(wrapper -> wrapper.like("title", keyword).or().like("problem_id", keyword)
                    .or().like("source", keyword).or().like("author", keyword));
        }
        query.orderByDesc("id");
        IPage<Problem> result = problemEntityService.page(new Page<>(page, limit), query);

        List<Long> pids = result.getRecords().stream().map(Problem::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagsByPid = tagNamesByPid(pids);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Problem problem : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", problem.getId());
            row.put("problem_id", problem.getProblemId());
            row.put("title", problem.getTitle());
            row.put("type", AgentViews.problemType(problem.getType()));
            row.put("auth", AgentViews.problemAuth(problem.getAuth()));
            row.put("difficulty", problem.getDifficulty());
            row.put("judge_mode", problem.getJudgeMode());
            row.put("is_remote", Boolean.TRUE.equals(problem.getIsRemote()));
            row.put("source", problem.getSource());
            row.put("author", problem.getAuthor());
            row.put("tags", tagsByPid.getOrDefault(problem.getId(), Collections.emptyList()));
            row.put("version", AgentViews.version(problem.getGmtModified()));
            row.put("gmt_modified", AgentViews.time(problem.getGmtModified(), zone));
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", rows);
        body.put("page", page);
        body.put("limit", limit);
        body.put("total", result.getTotal());
        body.put("has_more", (long) page * limit < result.getTotal());
        return body;
    }

    public Map<String, Object> get(String problemId, ZoneId zone) {
        ProblemState state = load(requireProblem(problemId));
        return view(state, zone);
    }

    public Map<String, Object> listTestCases(String problemId, boolean includeContent, int maxChars) {
        ProblemState state = load(requireProblem(problemId));
        Problem problem = state.problem;
        List<Map<String, Object>> rows = new ArrayList<>();
        String dir = testcaseDir(problem.getId());
        boolean upload = Boolean.TRUE.equals(problem.getIsUploadCase());
        int index = 1;
        for (ProblemCase problemCase : state.cases) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", index++);
            if (upload) {
                row.put("input_file", problemCase.getInput());
                row.put("output_file", problemCase.getOutput());
            }
            row.put("score", problemCase.getScore());
            row.put("group_num", problemCase.getGroupNum());
            if (includeContent) {
                putContent(row, "input", upload ? readFile(dir, problemCase.getInput()) : problemCase.getInput(), maxChars);
                putContent(row, "output", upload ? readFile(dir, problemCase.getOutput()) : problemCase.getOutput(), maxChars);
            }
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("problem_id", problem.getProblemId());
        body.put("mode", upload ? "upload" : "manual");
        body.put("count", rows.size());
        body.put("io_score", problem.getIoScore());
        body.put("data", rows);
        return body;
    }

    // ================================================================== 新增

    public Map<String, Object> create(AgentBody body, MultipartFile zip, AgentCaller caller, ZoneId zone) {
        Problem problem = new Problem()
                .setType(0)
                .setAuth(1)
                .setTimeLimit(1000)
                .setMemoryLimit(256)
                .setStackLimit(128)
                .setCodeShare(true)
                .setIsRemote(false)
                .setIsGroup(false)
                .setJudgeMode(Constants.JudgeMode.DEFAULT.getMode())
                .setJudgeCaseMode(Constants.JudgeCaseMode.DEFAULT.getMode())
                .setIsRemoveEndBlank(false)
                .setOpenCaseResult(true)
                .setIsFileIO(false)
                .setExamples("")
                .setDescription("")
                .setInput("")
                .setOutput("")
                .setHint("")
                .setSource("")
                .setAuthor(caller.getUsername());
        if (body.has("type")) {
            problem.setType(AgentViews.problemTypeValue(body.string("type")));
        }
        body.requiredString("problem_id");
        body.requiredString("title");
        applyFields(problem, body);
        if (problem.getDifficulty() == null) {
            problem.setDifficulty(defaultDifficulty());
        }

        List<Language> allLanguages = allLanguages();
        List<Language> languages = body.has("languages")
                ? resolveLanguages(body.stringList("languages", 200), allLanguages) : allLanguages;
        List<String> warnings = new ArrayList<>();
        List<Tag> tags = body.has("tags")
                ? resolveTags(body.stringList("tags", 50), warnings) : new ArrayList<>();
        List<CodeTemplate> templates = body.has("code_templates")
                ? resolveTemplates(body, languages, Collections.emptyList(), null) : new ArrayList<>();
        checkSpj(problem, allLanguages);
        validate(problem, true);

        TestCaseInput cases = null;
        try {
            if (zip != null) {
                if (body.has("test_cases")) {
                    throw AgentApiException.badRequest("上传 zip 测试数据时不能同时传 test_cases");
                }
                cases = unzipTestCases(zip, problem, body.objectList("case_scores", CASE_SCORE_FIELDS, MAX_TEST_CASES));
            } else {
                if (body.has("case_scores")) {
                    throw AgentApiException.badRequest("case_scores 只能与 zip 测试数据一起使用；手动测试点请在 test_cases 中写 score");
                }
                List<AgentBody> items = body.objectList("test_cases", TEST_CASE_FIELDS, MAX_TEST_CASES);
                if (items == null || items.isEmpty()) {
                    throw AgentApiException.badRequest("新建题目必须提供测试数据：JSON 中的 test_cases，或以 multipart 上传 zip（file 字段）");
                }
                cases = manualTestCases(items, problem);
            }
            problem.setIsUploadCase(cases.upload);
            compileSpj(problem);

            ProblemState after = new ProblemState(problem, tags, languages, templates, cases.cases);
            Map<String, Object> preview = view(after, zone);
            if (caller.isDryRun()) {
                return result(true, "create_problem", null, preview, warnings, null);
            }

            ProblemDTO dto = new ProblemDTO()
                    .setProblem(problem)
                    .setSamples(cases.cases)
                    .setIsUploadTestCase(cases.upload)
                    .setUploadTestcaseDir(cases.dir)
                    .setJudgeMode(problem.getJudgeMode())
                    .setLanguages(languages)
                    .setTags(tags)
                    .setCodeTemplates(templates);
            AgentManagerCalls.run(() -> adminProblemManager.addProblem(dto));
            cases.handedOver = true;

            Map<String, Object> created = view(load(requireProblem(problem.getProblemId())), zone);
            changeLogService.record(caller.getTokenId(), caller.getOperator(), "create_problem", "problem",
                    problem.getProblemId(), "新建题目 " + problem.getProblemId() + " " + problem.getTitle(),
                    null, editable(created));
            return result(false, "create_problem", null, created, warnings, null);
        } finally {
            if (cases != null) {
                cases.cleanupIfNotHandedOver();
            }
        }
    }

    // ================================================================== 修改

    public Map<String, Object> update(String problemId, AgentBody body, AgentCaller caller, ZoneId zone) {
        Problem current = requireEditable(problemId);
        checkVersion(body.longValue("expected_version"), current.getGmtModified(), "题目");
        ProblemState before = load(current);

        Problem next = copy(current);
        applyFields(next, body);
        List<Language> allLanguages = allLanguages();
        List<Language> languages = body.has("languages")
                ? resolveLanguages(body.stringList("languages", 200), allLanguages) : before.languages;
        List<String> warnings = new ArrayList<>();
        List<Tag> tags = body.has("tags") ? resolveTags(body.stringList("tags", 50), warnings) : before.tags;
        List<CodeTemplate> templates = body.has("code_templates")
                ? resolveTemplates(body, languages, before.templates, current.getId())
                : keepTemplates(before.templates, languages);
        checkSpj(next, allLanguages);

        boolean judgeCaseModeChanged = !Objects.equals(current.getJudgeCaseMode(), next.getJudgeCaseMode());
        List<ProblemCase> cases = copyCases(before.cases);
        if (judgeCaseModeChanged && isSubtask(next.getJudgeCaseMode())) {
            for (ProblemCase problemCase : cases) {
                if (problemCase.getGroupNum() == null) {
                    problemCase.setGroupNum(1);
                }
            }
            warnings.add("用例模式改为子任务，未设置分组的测试点已归入第 1 组；如需分组请用测试数据接口重新上传");
        }
        if (Constants.Contest.TYPE_OI.getCode().equals(next.getType()) && hasMissingScore(cases)) {
            throw AgentApiException.badRequest("OI 题目的测试点缺少分数，请先用测试数据接口重新上传并给出 score");
        }
        validate(next, false);

        ProblemState after = new ProblemState(next, tags, languages, templates, cases);
        Map<String, Object> beforeView = editable(view(before, zone));
        Map<String, Object> afterView = editable(view(after, zone));
        Map<String, Object> changes = AgentViews.diff(beforeView, afterView);
        if (changes.isEmpty()) {
            Map<String, Object> unchanged = result(caller.isDryRun(), "update_problem", changes,
                    view(before, zone), warnings, null);
            unchanged.put("changed", false);
            return unchanged;
        }

        boolean modeCodeChanged = !Objects.equals(current.getJudgeMode(), next.getJudgeMode())
                || !Objects.equals(current.getSpjCode(), next.getSpjCode())
                || !Objects.equals(current.getSpjLanguage(), next.getSpjLanguage());
        if (modeCodeChanged) {
            compileSpj(next);
        }
        if (caller.isDryRun()) {
            return result(true, "update_problem", changes, view(after, zone), warnings, null);
        }

        next.setModifiedUser(caller.getUsername());
        ProblemDTO dto = new ProblemDTO()
                .setProblem(next)
                .setSamples(cases)
                .setIsUploadTestCase(Boolean.TRUE.equals(next.getIsUploadCase()))
                .setUploadTestcaseDir(null)
                .setJudgeMode(next.getJudgeMode())
                .setChangeModeCode(modeCodeChanged)
                .setChangeJudgeCaseMode(judgeCaseModeChanged)
                .setLanguages(languages)
                .setTags(tags)
                .setCodeTemplates(templates);
        AgentManagerCalls.run(() -> adminProblemManager.updateProblem(dto));

        Map<String, Object> updated = view(load(problemEntityService.getById(current.getId())), zone);
        changeLogService.record(caller.getTokenId(), caller.getOperator(), "update_problem", "problem",
                String.valueOf(updated.get("problem_id")),
                "修改题目 " + current.getProblemId() + "：" + String.join(", ", changes.keySet()),
                beforeView, editable(updated));
        return result(false, "update_problem", changes, updated, warnings, null);
    }

    // ================================================================== 测试数据

    public Map<String, Object> replaceTestCases(String problemId, AgentBody body, MultipartFile zip,
                                                AgentCaller caller, ZoneId zone) {
        Problem current = requireEditable(problemId);
        checkVersion(body.longValue("expected_version"), current.getGmtModified(), "题目");
        ProblemState before = load(current);
        Problem next = copy(current);

        TestCaseInput cases;
        if (zip != null) {
            if (body.has("test_cases")) {
                throw AgentApiException.badRequest("上传 zip 时不能同时传 test_cases");
            }
            cases = unzipTestCases(zip, next, body.objectList("case_scores", CASE_SCORE_FIELDS, MAX_TEST_CASES));
        } else {
            if (body.has("case_scores")) {
                throw AgentApiException.badRequest("case_scores 只能与 zip 一起使用；手动测试点请在 test_cases 中写 score");
            }
            List<AgentBody> items = body.objectList("test_cases", TEST_CASE_FIELDS, MAX_TEST_CASES);
            if (items == null || items.isEmpty()) {
                throw AgentApiException.badRequest("请提供 test_cases（JSON）或以 multipart 上传 zip（file 字段）");
            }
            cases = manualTestCases(items, next);
        }
        try {
            next.setIsUploadCase(cases.upload);
            Map<String, Object> changes = new LinkedHashMap<>();
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("before", caseSummary(current, before.cases));
            change.put("after", caseSummary(next, cases.cases));
            changes.put("test_cases", change);
            if (caller.isDryRun()) {
                return result(true, "replace_test_cases", changes, null, Collections.emptyList(),
                        current.getProblemId());
            }

            next.setModifiedUser(caller.getUsername());
            ProblemDTO dto = new ProblemDTO()
                    .setProblem(next)
                    .setSamples(cases.cases)
                    .setIsUploadTestCase(cases.upload)
                    .setUploadTestcaseDir(cases.dir)
                    .setJudgeMode(next.getJudgeMode())
                    .setChangeModeCode(false)
                    .setChangeJudgeCaseMode(false)
                    .setLanguages(before.languages)
                    .setTags(before.tags)
                    .setCodeTemplates(before.templates);
            AgentManagerCalls.run(() -> adminProblemManager.updateProblem(dto));
            cases.handedOver = true;

            ProblemState updated = load(problemEntityService.getById(current.getId()));
            Map<String, Object> summary = caseSummary(updated.problem, updated.cases);
            changeLogService.record(caller.getTokenId(), caller.getOperator(), "replace_test_cases", "problem",
                    current.getProblemId(), "替换题目 " + current.getProblemId() + " 的测试数据（"
                            + updated.cases.size() + " 个测试点）",
                    change.get("before"), summary);
            Map<String, Object> response = result(false, "replace_test_cases", changes, null,
                    Collections.emptyList(), current.getProblemId());
            response.put("version", AgentViews.version(updated.problem.getGmtModified()));
            return response;
        } finally {
            cases.cleanupIfNotHandedOver();
        }
    }

    // ================================================================== 内部：字段解析

    private void applyFields(Problem problem, AgentBody body) {
        if (body.has("problem_id")) {
            String problemId = body.requiredString("problem_id").trim().toUpperCase();
            if (!problemId.matches("[A-Z0-9_-]{1,50}")) {
                throw AgentApiException.badRequest("problem_id 只能包含字母、数字、- 和 _，且不超过 50 个字符");
            }
            problem.setProblemId(problemId);
        }
        if (body.has("title")) {
            problem.setTitle(body.requiredString("title").trim());
        }
        if (body.has("description")) {
            problem.setDescription(nullToEmpty(body.string("description")));
        }
        if (body.has("input")) {
            problem.setInput(nullToEmpty(body.string("input")));
        }
        if (body.has("output")) {
            problem.setOutput(nullToEmpty(body.string("output")));
        }
        if (body.has("hint")) {
            problem.setHint(nullToEmpty(body.string("hint")));
        }
        if (body.has("source")) {
            problem.setSource(nullToEmpty(body.string("source")));
        }
        if (body.has("examples")) {
            List<AgentBody> items = body.objectList("examples", EXAMPLE_FIELDS, 50);
            List<Map<String, String>> examples = new ArrayList<>();
            if (items != null) {
                for (AgentBody item : items) {
                    Map<String, String> example = new LinkedHashMap<>();
                    example.put("input", nullToEmpty(item.string("input")));
                    example.put("output", nullToEmpty(item.string("output")));
                    examples.add(example);
                }
            }
            problem.setExamples(AgentViews.formatExamples(examples));
        }
        if (body.has("difficulty")) {
            problem.setDifficulty(body.integer("difficulty", 0, Integer.MAX_VALUE));
        }
        if (body.has("auth")) {
            problem.setAuth(AgentViews.problemAuthValue(body.requiredString("auth")));
        }
        if (body.has("time_limit")) {
            problem.setTimeLimit(requiredInt(body, "time_limit", 1, 60_000));
        }
        if (body.has("memory_limit")) {
            problem.setMemoryLimit(requiredInt(body, "memory_limit", 1, 8192));
        }
        if (body.has("stack_limit")) {
            problem.setStackLimit(requiredInt(body, "stack_limit", 1, 8192));
        }
        if (body.has("judge_mode")) {
            problem.setJudgeMode(body.choice("judge_mode", "default", "spj", "interactive"));
            if (problem.getJudgeMode() == null) {
                throw AgentApiException.badRequest("judge_mode 不能为空");
            }
        }
        if (body.has("judge_case_mode")) {
            problem.setJudgeCaseMode(body.choice("judge_case_mode",
                    "default", "ergodic_without_error", "subtask_lowest", "subtask_average"));
            if (problem.getJudgeCaseMode() == null) {
                throw AgentApiException.badRequest("judge_case_mode 不能为空");
            }
        }
        if (body.has("spj_language")) {
            problem.setSpjLanguage(body.string("spj_language"));
        }
        if (body.has("spj_code")) {
            problem.setSpjCode(body.string("spj_code"));
        }
        if (body.has("code_share")) {
            problem.setCodeShare(requiredBool(body, "code_share"));
        }
        if (body.has("remove_end_blank")) {
            problem.setIsRemoveEndBlank(requiredBool(body, "remove_end_blank"));
        }
        if (body.has("open_case_result")) {
            problem.setOpenCaseResult(requiredBool(body, "open_case_result"));
        }
        if (body.has("file_io")) {
            problem.setIsFileIO(requiredBool(body, "file_io"));
        }
        if (body.has("io_read_file_name")) {
            problem.setIoReadFileName(body.string("io_read_file_name"));
        }
        if (body.has("io_write_file_name")) {
            problem.setIoWriteFileName(body.string("io_write_file_name"));
        }
        if (Boolean.TRUE.equals(problem.getIsFileIO())
                && (isBlank(problem.getIoReadFileName()) || isBlank(problem.getIoWriteFileName()))) {
            throw AgentApiException.badRequest("file_io 为 true 时必须提供 io_read_file_name 与 io_write_file_name");
        }
    }

    private static int requiredInt(AgentBody body, String name, int min, int max) {
        Integer value = body.integer(name, min, max);
        if (value == null) {
            throw AgentApiException.badRequest(name + " 不能为空");
        }
        return value;
    }

    private static boolean requiredBool(AgentBody body, String name) {
        Boolean value = body.bool(name);
        if (value == null) {
            throw AgentApiException.badRequest(name + " 不能为空");
        }
        return value;
    }

    private void validate(Problem problem, boolean creating) {
        AgentManagerCalls.run(() -> {
            if (creating) {
                problemValidator.validateProblem(problem);
            } else {
                problemValidator.validateProblemUpdate(problem);
            }
        });
        Problem existed = problemEntityService.getOne(new QueryWrapper<Problem>()
                .select("id").eq("problem_id", problem.getProblemId()), false);
        if (existed != null && (creating || !existed.getId().equals(problem.getId()))) {
            throw new AgentApiException(409, "conflict", "题号 " + problem.getProblemId() + " 已被其他题目使用");
        }
    }

    private void checkSpj(Problem problem, List<Language> allLanguages) {
        if (Constants.JudgeMode.DEFAULT.getMode().equals(problem.getJudgeMode())) {
            problem.setSpjCode(null).setSpjLanguage(null);
            return;
        }
        if (isBlank(problem.getSpjCode()) || isBlank(problem.getSpjLanguage())) {
            throw AgentApiException.badRequest("judge_mode 为 " + problem.getJudgeMode()
                    + " 时必须提供 spj_code 与 spj_language");
        }
        List<String> spjLanguages = allLanguages.stream().filter(lang -> Boolean.TRUE.equals(lang.getIsSpj()))
                .map(Language::getName).collect(Collectors.toList());
        String matched = spjLanguages.stream().filter(name -> name.equalsIgnoreCase(problem.getSpjLanguage().trim()))
                .findFirst().orElse(null);
        if (matched == null) {
            throw AgentApiException.badRequest("spj_language 不支持: " + problem.getSpjLanguage()
                    + "；可用: " + String.join(", ", spjLanguages));
        }
        problem.setSpjLanguage(matched);
    }

    /** 特判 / 交互程序先交给判题机编译，编译失败直接返回编译信息，与后台页面“编译”按钮相同 */
    private void compileSpj(Problem problem) {
        if (Constants.JudgeMode.DEFAULT.getMode().equals(problem.getJudgeMode())) {
            return;
        }
        CompileDTO compile = new CompileDTO();
        compile.setCode(problem.getSpjCode());
        compile.setLanguage(problem.getSpjLanguage());
        compile.setPid(problem.getId());
        compile.setExtraFiles(parseExtraFiles(problem.getJudgeExtraFile()));
        CommonResult<?> result;
        try {
            result = "interactive".equals(problem.getJudgeMode())
                    ? adminProblemManager.compileInteractive(compile)
                    : adminProblemManager.compileSpj(compile);
        } catch (RuntimeException e) {
            log.warn("[Agent API] SPJ compile dispatch failed", e);
            throw new AgentApiException(503, "judge_unavailable", "无法连接判题机编译特判程序，请稍后重试");
        }
        if (result == null || result.getStatus() == null || result.getStatus() != 200) {
            throw new AgentApiException(422, "spj_compile_error", "特判/交互程序编译失败："
                    + (result == null ? "判题机无响应" : result.getMsg()));
        }
    }

    @SuppressWarnings("unchecked")
    private static HashMap<String, String> parseExtraFiles(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return AgentJson.MAPPER.readValue(json, HashMap.class);
        } catch (IOException e) {
            return null;
        }
    }

    private List<Language> allLanguages() {
        List<Language> languages = languageEntityService.list(new QueryWrapper<Language>().eq("oj", OJ));
        languages.sort(Comparator.comparing((Language lang) -> lang.getSeq() == null ? 0 : lang.getSeq(),
                Comparator.reverseOrder()).thenComparing(Language::getId));
        return languages;
    }

    private static List<Language> resolveLanguages(List<String> names, List<Language> allLanguages) {
        if (names == null || names.isEmpty()) {
            throw AgentApiException.badRequest("languages 至少包含一种语言");
        }
        List<Language> result = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String name : names) {
            Language matched = allLanguages.stream().filter(lang -> lang.getName().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
            if (matched == null) {
                unknown.add(name);
            } else if (!result.contains(matched)) {
                result.add(matched);
            }
        }
        if (!unknown.isEmpty()) {
            throw AgentApiException.badRequest("未知语言: " + String.join(", ", unknown) + "；可用: "
                    + allLanguages.stream().map(Language::getName).collect(Collectors.joining(", ")));
        }
        return result;
    }

    /** 已有标签按名称复用（只匹配本站、非团队标签），不存在的标签会新建 */
    private List<Tag> resolveTags(List<String> names, List<String> warnings) {
        List<Tag> result = new ArrayList<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            if (name.length() > 255) {
                throw AgentApiException.badRequest("标签名称过长: " + name);
            }
            Tag existed = tagEntityService.getOne(new QueryWrapper<Tag>()
                    .eq("name", name).eq("oj", OJ).isNull("gid"), false);
            if (existed == null) {
                warnings.add("将新建标签: " + name);
                result.add(new Tag().setName(name).setOj(OJ));
            } else {
                result.add(existed);
            }
        }
        return result;
    }

    private static List<CodeTemplate> resolveTemplates(AgentBody body, List<Language> languages,
                                                       List<CodeTemplate> existing, Long pid) {
        List<AgentBody> items = body.objectList("code_templates", TEMPLATE_FIELDS, 200);
        List<CodeTemplate> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        Set<Long> seen = new HashSet<>();
        for (AgentBody item : items) {
            String name = item.requiredString("language");
            String code = item.requiredString("code");
            Language language = languages.stream().filter(lang -> lang.getName().equalsIgnoreCase(name))
                    .findFirst().orElseThrow(() -> AgentApiException.badRequest(
                            "代码模板的语言 " + name + " 不在题目允许的 languages 中"));
            if (!seen.add(language.getId())) {
                throw AgentApiException.badRequest("代码模板的语言重复: " + name);
            }
            CodeTemplate old = existing.stream().filter(t -> language.getId().equals(t.getLid()))
                    .findFirst().orElse(null);
            result.add(new CodeTemplate().setId(old == null ? null : old.getId()).setPid(pid)
                    .setLid(language.getId()).setCode(code).setStatus(true));
        }
        return result;
    }

    /** 未修改代码模板但去掉了某种语言时，与后台页面一致：只保留仍允许的语言的模板 */
    private static List<CodeTemplate> keepTemplates(List<CodeTemplate> existing, List<Language> languages) {
        Set<Long> allowed = languages.stream().map(Language::getId).collect(Collectors.toSet());
        return existing.stream().filter(t -> allowed.contains(t.getLid())).collect(Collectors.toList());
    }

    private Integer defaultDifficulty() {
        List<Integer> values = jdbcTemplate.queryForList(
                "SELECT difficulty_value FROM problem_difficulty_config ORDER BY sort_order, difficulty_value LIMIT 1",
                Integer.class);
        if (values.isEmpty()) {
            throw AgentApiException.badRequest("站点尚未配置题目难度，请先在后台配置难度或传入 difficulty");
        }
        return values.get(0);
    }

    // ================================================================== 内部：测试数据

    private static final class TestCaseInput {
        final List<ProblemCase> cases;
        final boolean upload;
        final String dir;
        boolean handedOver;

        TestCaseInput(List<ProblemCase> cases, boolean upload, String dir) {
            this.cases = cases;
            this.upload = upload;
            this.dir = dir;
        }

        /** zip 解压到临时目录后，若最终没有交给后台初始化（演练或出错），在此删除临时目录 */
        void cleanupIfNotHandedOver() {
            if (!handedOver && dir != null) {
                FileUtil.del(dir);
            }
        }
    }

    private TestCaseInput manualTestCases(List<AgentBody> items, Problem problem) {
        List<ProblemCase> cases = new ArrayList<>();
        for (AgentBody item : items) {
            String input = nullToEmpty(item.string("input"));
            String output = nullToEmpty(item.string("output"));
            if (input.isEmpty() && output.isEmpty()) {
                throw AgentApiException.badRequest("测试点的 input 与 output 不能同时为空");
            }
            cases.add(new ProblemCase().setInput(input).setOutput(output).setStatus(0)
                    .setScore(item.integer("score", 0, 1000))
                    .setGroupNum(item.integer("group_num", 1, 1000)));
        }
        applyScores(cases, problem);
        return new TestCaseInput(cases, false, null);
    }

    @SuppressWarnings("unchecked")
    private TestCaseInput unzipTestCases(MultipartFile zip, Problem problem, List<AgentBody> caseScores) {
        String name = zip.getOriginalFilename() == null ? "" : zip.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".zip")) {
            throw AgentApiException.badRequest("测试数据必须是 .zip 压缩包（内含 1.in/1.out 等文件）");
        }
        Map<Object, Object> unzipped = AgentManagerCalls.call(
                () -> testCaseManager.uploadTestcaseZip(zip, null, problem.getJudgeCaseMode()));
        String dir = (String) unzipped.get("fileListDir");
        TestCaseInput result = null;
        try {
            List<Map<String, Object>> files = (List<Map<String, Object>>) unzipped.get("fileList");
            Map<String, ProblemCase> byInput = new LinkedHashMap<>();
            List<ProblemCase> cases = new ArrayList<>();
            for (Map<String, Object> file : files) {
                Object groupNum = file.get("groupNum");
                ProblemCase problemCase = new ProblemCase()
                        .setInput(String.valueOf(file.get("input")))
                        .setOutput(String.valueOf(file.get("output")))
                        .setStatus(0)
                        .setGroupNum(groupNum instanceof Number ? ((Number) groupNum).intValue() : null);
                cases.add(problemCase);
                byInput.put(problemCase.getInput(), problemCase);
            }
            if (cases.isEmpty()) {
                throw AgentApiException.badRequest("压缩包中没有找到 .in 测试输入文件");
            }
            if (caseScores != null) {
                for (AgentBody item : caseScores) {
                    String input = item.requiredString("input");
                    ProblemCase problemCase = byInput.get(input);
                    if (problemCase == null) {
                        throw AgentApiException.badRequest("case_scores 中的 " + input + " 不在压缩包中；压缩包内的输入文件: "
                                + String.join(", ", byInput.keySet()));
                    }
                    if (item.has("score")) {
                        problemCase.setScore(item.integer("score", 0, 1000));
                    }
                    if (item.has("group_num")) {
                        problemCase.setGroupNum(item.integer("group_num", 1, 1000));
                    }
                }
            }
            applyScores(cases, problem);
            result = new TestCaseInput(cases, true, dir);
            return result;
        } finally {
            if (result == null) {
                FileUtil.del(dir);
            }
        }
    }

    /**
     * OI 题目：全部未给分时平均分配 100 分；只给了部分则报错。ACM 题目分数不参与评测，未给全时同样平均分配。
     * 子任务模式下未给分组的测试点归入第 1 组。
     */
    static void applyScores(List<ProblemCase> cases, Problem problem) {
        boolean oi = Constants.Contest.TYPE_OI.getCode().equals(problem.getType());
        long missing = cases.stream().filter(c -> c.getScore() == null).count();
        if (missing > 0) {
            if (oi && missing < cases.size()) {
                throw AgentApiException.badRequest("OI 题目的测试点分数要么全部给出，要么全部不给（自动平均分配 100 分）");
            }
            int[] scores = AgentViews.distributeScores(cases.size());
            for (int i = 0; i < cases.size(); i++) {
                cases.get(i).setScore(scores[i]);
            }
        }
        if (isSubtask(problem.getJudgeCaseMode())) {
            for (ProblemCase problemCase : cases) {
                if (problemCase.getGroupNum() == null) {
                    problemCase.setGroupNum(1);
                }
            }
        }
    }

    private static boolean isSubtask(String judgeCaseMode) {
        return Constants.JudgeCaseMode.SUBTASK_AVERAGE.getMode().equals(judgeCaseMode)
                || Constants.JudgeCaseMode.SUBTASK_LOWEST.getMode().equals(judgeCaseMode);
    }

    private static boolean hasMissingScore(List<ProblemCase> cases) {
        return cases.stream().anyMatch(c -> c.getScore() == null);
    }

    private static List<ProblemCase> copyCases(List<ProblemCase> cases) {
        List<ProblemCase> result = new ArrayList<>();
        for (ProblemCase problemCase : cases) {
            ProblemCase copy = new ProblemCase();
            BeanUtil.copyProperties(problemCase, copy);
            result.add(copy);
        }
        return result;
    }

    private Map<String, Object> caseSummary(Problem problem, List<ProblemCase> cases) {
        boolean upload = Boolean.TRUE.equals(problem.getIsUploadCase());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", upload ? "upload" : "manual");
        summary.put("count", cases.size());
        int total = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProblemCase problemCase : cases) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (upload) {
                row.put("input_file", problemCase.getInput());
                row.put("output_file", problemCase.getOutput());
            } else {
                row.put("input_chars", problemCase.getInput() == null ? 0 : problemCase.getInput().length());
                row.put("output_chars", problemCase.getOutput() == null ? 0 : problemCase.getOutput().length());
            }
            row.put("score", problemCase.getScore());
            if (isSubtask(problem.getJudgeCaseMode())) {
                row.put("group_num", problemCase.getGroupNum());
            }
            if (problemCase.getScore() != null) {
                total += problemCase.getScore();
            }
            rows.add(row);
        }
        summary.put("total_score", total);
        summary.put("cases", rows);
        return summary;
    }

    private static String testcaseDir(Long pid) {
        return Constants.File.TESTCASE_BASE_FOLDER.getPath() + File.separator + "problem_" + pid;
    }

    private static String readFile(String dir, String name) {
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        File file = new File(dir, name);
        if (!file.isFile()) {
            return null;
        }
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[(int) Math.min(file.length(), 4L * 1024 * 1024)];
            int read = 0;
            while (read < buffer.length) {
                int count = input.read(buffer, read, buffer.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[Agent API] Failed to read test case file {}", file, e);
            return null;
        }
    }

    private static void putContent(Map<String, Object> row, String key, String content, int maxChars) {
        if (content == null) {
            row.put(key, null);
            row.put(key + "_missing", true);
            return;
        }
        boolean truncated = content.length() > maxChars;
        row.put(key, truncated ? content.substring(0, maxChars) : content);
        row.put(key + "_chars", content.length());
        if (truncated) {
            row.put(key + "_truncated", true);
        }
    }

    // ================================================================== 内部：加载与视图

    static final class ProblemState {
        final Problem problem;
        final List<Tag> tags;
        final List<Language> languages;
        final List<CodeTemplate> templates;
        final List<ProblemCase> cases;

        ProblemState(Problem problem, List<Tag> tags, List<Language> languages, List<CodeTemplate> templates,
                     List<ProblemCase> cases) {
            this.problem = problem;
            this.tags = tags;
            this.languages = languages;
            this.templates = templates;
            this.cases = cases;
        }
    }

    Problem requireProblem(String problemId) {
        if (problemId == null || problemId.trim().isEmpty()) {
            throw AgentApiException.badRequest("缺少题号");
        }
        Problem problem = problemEntityService.getOne(new QueryWrapper<Problem>()
                .eq("problem_id", problemId.trim().toUpperCase()), false);
        if (problem == null) {
            throw AgentApiException.notFound("题目不存在: " + problemId + "（请使用题号 problem_id，如 P1001）");
        }
        return problem;
    }

    Problem requireEditable(String problemId) {
        Problem problem = requireProblem(problemId);
        if (Boolean.TRUE.equals(problem.getIsRemote())) {
            throw new AgentApiException(403, "not_supported", "远程 OJ 题目不支持通过接口修改，请在后台操作");
        }
        if (Boolean.TRUE.equals(problem.getIsGroup())) {
            throw new AgentApiException(403, "not_supported", "团队题目不支持通过接口修改，请在团队管理中操作");
        }
        return problem;
    }

    private ProblemState load(Problem problem) {
        Long pid = problem.getId();
        List<Long> tids = problemTagEntityService.list(new QueryWrapper<ProblemTag>().eq("pid", pid))
                .stream().map(ProblemTag::getTid).collect(Collectors.toList());
        List<Tag> tags = tids.isEmpty() ? new ArrayList<>() : new ArrayList<>(tagEntityService.listByIds(tids));
        tags.sort(Comparator.comparing(Tag::getId));

        List<Long> lids = problemLanguageEntityService.list(new QueryWrapper<ProblemLanguage>().eq("pid", pid))
                .stream().map(ProblemLanguage::getLid).collect(Collectors.toList());
        List<Language> languages = lids.isEmpty() ? new ArrayList<>()
                : new ArrayList<>(languageEntityService.listByIds(lids));
        languages.sort(Comparator.comparing((Language lang) -> lang.getSeq() == null ? 0 : lang.getSeq(),
                Comparator.reverseOrder()).thenComparing(Language::getId));

        List<CodeTemplate> templates = codeTemplateEntityService.list(
                new QueryWrapper<CodeTemplate>().eq("pid", pid).orderByAsc("id"));

        QueryWrapper<ProblemCase> caseQuery = new QueryWrapper<ProblemCase>().eq("pid", pid).eq("status", 0);
        if (Boolean.TRUE.equals(problem.getIsUploadCase())) {
            caseQuery.last("order by length(input) asc, input asc");
        } else {
            caseQuery.orderByAsc("id");
        }
        List<ProblemCase> cases = problemCaseEntityService.list(caseQuery);
        return new ProblemState(problem, tags, languages, templates, cases);
    }

    private Map<String, Object> view(ProblemState state, ZoneId zone) {
        Problem p = state.problem;
        Map<Long, String> languageNames = new HashMap<>();
        for (Language language : allLanguages()) {
            languageNames.put(language.getId(), language.getName());
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", p.getId());
        view.put("problem_id", p.getProblemId());
        view.put("title", p.getTitle());
        view.put("type", AgentViews.problemType(p.getType()));
        view.put("auth", AgentViews.problemAuth(p.getAuth()));
        view.put("difficulty", p.getDifficulty());
        view.put("author", p.getAuthor());
        view.put("source", p.getSource());
        view.put("time_limit", p.getTimeLimit());
        view.put("memory_limit", p.getMemoryLimit());
        view.put("stack_limit", p.getStackLimit());
        view.put("judge_mode", p.getJudgeMode());
        view.put("judge_case_mode", p.getJudgeCaseMode());
        view.put("spj_language", p.getSpjLanguage());
        view.put("spj_code", p.getSpjCode());
        view.put("description", p.getDescription());
        view.put("input", p.getInput());
        view.put("output", p.getOutput());
        view.put("examples", AgentViews.parseExamples(p.getExamples()));
        view.put("hint", p.getHint());
        view.put("tags", state.tags.stream().map(Tag::getName).collect(Collectors.toList()));
        view.put("languages", state.languages.stream().map(Language::getName).collect(Collectors.toList()));
        List<Map<String, Object>> templates = new ArrayList<>();
        for (CodeTemplate template : state.templates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("language", languageNames.getOrDefault(template.getLid(), String.valueOf(template.getLid())));
            item.put("code", template.getCode());
            templates.add(item);
        }
        view.put("code_templates", templates);
        view.put("code_share", p.getCodeShare());
        view.put("remove_end_blank", p.getIsRemoveEndBlank());
        view.put("open_case_result", p.getOpenCaseResult());
        view.put("file_io", p.getIsFileIO());
        view.put("io_read_file_name", p.getIoReadFileName());
        view.put("io_write_file_name", p.getIoWriteFileName());
        view.put("test_cases", caseSummaryShort(p, state.cases));
        view.put("is_remote", Boolean.TRUE.equals(p.getIsRemote()));
        view.put("is_group", Boolean.TRUE.equals(p.getIsGroup()));
        view.put("modified_user", p.getModifiedUser());
        view.put("version", AgentViews.version(p.getGmtModified()));
        view.put("gmt_create", AgentViews.time(p.getGmtCreate(), zone));
        view.put("gmt_modified", AgentViews.time(p.getGmtModified(), zone));
        return view;
    }

    private static Map<String, Object> caseSummaryShort(Problem problem, List<ProblemCase> cases) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", Boolean.TRUE.equals(problem.getIsUploadCase()) ? "upload" : "manual");
        summary.put("count", cases.size());
        summary.put("total_score", cases.stream().mapToInt(c -> c.getScore() == null ? 0 : c.getScore()).sum());
        return summary;
    }

    static Map<String, Object> editable(Map<String, Object> view) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : EDITABLE_VIEW_KEYS) {
            result.put(key, view.get(key));
        }
        return result;
    }

    private Map<Long, List<String>> tagNamesByPid(List<Long> pids) {
        Map<Long, List<String>> result = new HashMap<>();
        if (pids.isEmpty()) {
            return result;
        }
        namedJdbcTemplate.query(
                "SELECT pt.pid, t.name FROM problem_tag pt JOIN tag t ON t.id = pt.tid "
                        + "WHERE pt.pid IN (:pids) ORDER BY pt.pid, t.id",
                new MapSqlParameterSource("pids", pids),
                rs -> {
                    result.computeIfAbsent(rs.getLong("pid"), key -> new ArrayList<>()).add(rs.getString("name"));
                });
        return result;
    }

    private static Problem copy(Problem problem) {
        Problem copy = new Problem();
        BeanUtil.copyProperties(problem, copy);
        return copy;
    }

    static void checkVersion(Long expected, java.util.Date gmtModified, String what) {
        Long current = AgentViews.version(gmtModified);
        if (expected != null && !expected.equals(current)) {
            throw new AgentApiException(409, "version_conflict", what + "在你读取之后已被修改（当前 version="
                    + current + "），请重新获取最新内容后再修改");
        }
    }

    static Map<String, Object> result(boolean dryRun, String action, Map<String, Object> changes,
                                      Map<String, Object> data, List<String> warnings, String problemId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dry_run", dryRun);
        body.put("action", action);
        if (problemId != null) {
            body.put("problem_id", problemId);
        }
        if (dryRun) {
            body.put("message", "演练模式：只做了校验，未写入任何数据。确认无误后去掉 dry_run 再调用一次。");
        }
        if (changes != null) {
            body.put("changes", changes);
        }
        if (warnings != null && !warnings.isEmpty()) {
            body.put("warnings", warnings);
        }
        if (data != null) {
            body.put("data", data);
        }
        return body;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
