package top.hcode.hoj.agent.manage;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import top.hcode.hoj.agent.AgentApiException;
import top.hcode.hoj.dao.training.TrainingCategoryEntityService;
import top.hcode.hoj.dao.training.TrainingEntityService;
import top.hcode.hoj.dao.training.TrainingProblemEntityService;
import top.hcode.hoj.manager.admin.training.AdminTrainingManager;
import top.hcode.hoj.manager.admin.training.AdminTrainingProblemManager;
import top.hcode.hoj.pojo.dto.TrainingDTO;
import top.hcode.hoj.pojo.dto.TrainingProblemDTO;
import top.hcode.hoj.pojo.entity.problem.Problem;
import top.hcode.hoj.pojo.entity.training.Training;
import top.hcode.hoj.pojo.entity.training.TrainingCategory;
import top.hcode.hoj.pojo.entity.training.TrainingProblem;
import top.hcode.hoj.validator.TrainingValidator;

import javax.annotation.Resource;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 训练（题单）管理：查询、新增、修改（含启用/停用）、增删训练中的题目与调整顺序。
 * 写操作委托给后台 {@link AdminTrainingManager} / {@link AdminTrainingProblemManager}。
 * 接口不提供删除训练：下线训练请把 status 设为 false。团队训练只读。
 */
@Service
public class AgentTrainingService {

    static final Set<String> COMMON_FIELDS = AgentBody.fields(
            "title", "description", "auth", "private_pwd", "rank", "status", "category");
    public static final Set<String> CREATE_FIELDS = AgentBody.fields(COMMON_FIELDS, "problems", "dry_run");
    public static final Set<String> UPDATE_FIELDS = AgentBody.fields(COMMON_FIELDS, "expected_version", "dry_run");
    public static final Set<String> ADD_PROBLEMS_FIELDS = AgentBody.fields("problems", "dry_run");
    /**
     * 训练中题目的展示 ID 在数据库中有外键指向 problem.problem_id（并随题号修改级联更新），
     * 因此不能自定义，固定为题目的题号。
     */
    static final Set<String> PROBLEM_ITEM_FIELDS = AgentBody.fields("problem_id", "rank");
    public static final Set<String> PROBLEM_PATCH_FIELDS = AgentBody.fields("rank", "dry_run");
    public static final Set<String> REORDER_FIELDS = AgentBody.fields("problem_ids", "dry_run");
    public static final Set<String> REMOVE_FIELDS = AgentBody.fields("dry_run");
    static final int MAX_PROBLEMS_PER_CALL = 200;

    private static final List<String> EDITABLE_VIEW_KEYS = java.util.Arrays.asList(
            "title", "description", "auth", "private_pwd", "rank", "status", "category");

    @Resource
    private TrainingEntityService trainingEntityService;
    @Resource
    private TrainingProblemEntityService trainingProblemEntityService;
    @Resource
    private TrainingCategoryEntityService trainingCategoryEntityService;
    @Resource
    private AdminTrainingManager adminTrainingManager;
    @Resource
    private AdminTrainingProblemManager adminTrainingProblemManager;
    @Resource
    private TrainingValidator trainingValidator;
    @Resource
    private AgentProblemService problemService;
    @Resource
    private AgentChangeLogService changeLogService;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    // ================================================================== 查询

    public Map<String, Object> list(String keyword, int page, int limit, ZoneId zone) {
        QueryWrapper<Training> query = new QueryWrapper<>();
        query.select(Training.class, info -> !"private_pwd".equals(info.getColumn()));
        query.eq("is_group", false);
        if (keyword != null) {
            query.and(wrapper -> wrapper.like("title", keyword).or().like("author", keyword));
        }
        query.orderByAsc("`rank`").orderByAsc("id");
        IPage<Training> result = trainingEntityService.page(new Page<>(page, limit), query);

        List<Long> tids = result.getRecords().stream().map(Training::getId).collect(Collectors.toList());
        Map<Long, Long> counts = new HashMap<>();
        Map<Long, Map<String, Object>> categories = new HashMap<>();
        if (!tids.isEmpty()) {
            MapSqlParameterSource args = new MapSqlParameterSource("tids", tids);
            namedJdbcTemplate.query("SELECT tid, COUNT(*) AS cnt FROM training_problem WHERE tid IN (:tids) GROUP BY tid",
                    args, rs -> {
                        counts.put(rs.getLong("tid"), rs.getLong("cnt"));
                    });
            namedJdbcTemplate.query("SELECT m.tid, c.id, c.name, c.color FROM mapping_training_category m "
                            + "JOIN training_category c ON c.id = m.cid WHERE m.tid IN (:tids)",
                    args, rs -> {
                        categories.put(rs.getLong("tid"), category(rs.getLong("id"), rs.getString("name"),
                                rs.getString("color")));
                    });
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Training training : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", training.getId());
            row.put("title", training.getTitle());
            row.put("auth", training.getAuth());
            row.put("status", training.getStatus());
            row.put("rank", training.getRank());
            row.put("author", training.getAuthor());
            row.put("category", categories.get(training.getId()));
            row.put("problem_count", counts.getOrDefault(training.getId(), 0L));
            row.put("version", AgentViews.version(training.getGmtModified()));
            row.put("gmt_modified", AgentViews.time(training.getGmtModified(), zone));
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

    public Map<String, Object> get(long tid, ZoneId zone) {
        return view(requireTraining(tid), zone);
    }

    // ================================================================== 新增 / 修改

    public Map<String, Object> create(AgentBody body, AgentCaller caller, ZoneId zone) {
        Training training = new Training()
                .setTitle(body.requiredString("title").trim())
                .setDescription("")
                .setAuth("Public")
                .setStatus(true)
                .setRank(0)
                .setAuthor(caller.getUsername())
                .setIsGroup(false);
        List<String> warnings = new ArrayList<>();
        applyFields(training, body);
        TrainingCategory category = resolveCategory(body.string("category"), true, warnings);
        List<PlannedProblem> planned = body.has("problems")
                ? planProblems(Collections.emptyList(),
                body.objectList("problems", PROBLEM_ITEM_FIELDS, MAX_PROBLEMS_PER_CALL), warnings)
                : Collections.emptyList();
        AgentManagerCalls.run(() -> trainingValidator.validateTraining(training));

        if (caller.isDryRun()) {
            Map<String, Object> preview = editableView(training, category);
            preview.put("problems", plannedView(planned));
            return AgentProblemService.result(true, "create_training", null, preview, warnings, null);
        }

        TrainingDTO dto = new TrainingDTO().setTraining(training).setTrainingCategory(category);
        AgentManagerCalls.run(() -> adminTrainingManager.addTraining(dto));
        Long tid = training.getId();
        if (tid == null) {
            throw new AgentApiException(500, "system_error", "训练创建失败");
        }
        insertProblems(tid, planned);

        Map<String, Object> created = view(trainingEntityService.getById(tid), zone);
        changeLogService.record(caller.getTokenId(), caller.getOperator(), "create_training", "training",
                String.valueOf(tid), "新建训练 " + tid + " " + training.getTitle()
                        + (planned.isEmpty() ? "" : "（含 " + planned.size() + " 道题）"),
                null, created);
        return AgentProblemService.result(false, "create_training", null, created, warnings, null);
    }

    public Map<String, Object> update(long tid, AgentBody body, AgentCaller caller, ZoneId zone) {
        Training current = requireTraining(tid);
        AgentProblemService.checkVersion(body.longValue("expected_version"), current.getGmtModified(), "训练");
        TrainingCategory currentCategory = currentCategory(tid);

        Training next = new Training();
        BeanUtil.copyProperties(current, next);
        List<String> warnings = new ArrayList<>();
        applyFields(next, body);
        TrainingCategory category = body.has("category")
                ? resolveCategory(body.string("category"), true, warnings) : currentCategory;
        if (category == null) {
            throw AgentApiException.badRequest("该训练还没有分类，请同时传入 category");
        }
        AgentManagerCalls.run(() -> trainingValidator.validateTraining(next));

        Map<String, Object> before = editableView(current, currentCategory);
        Map<String, Object> after = editableView(next, category);
        Map<String, Object> changes = AgentViews.diff(before, after);
        if ("Private".equals(next.getAuth()) && changes.containsKey("private_pwd")) {
            warnings.add("修改私有训练的密码会清空已报名该训练的用户，学生需要用新密码重新加入");
        }
        if (changes.isEmpty()) {
            Map<String, Object> unchanged = AgentProblemService.result(caller.isDryRun(), "update_training",
                    changes, view(current, zone), warnings, null);
            unchanged.put("changed", false);
            return unchanged;
        }
        if (caller.isDryRun()) {
            return AgentProblemService.result(true, "update_training", changes, after, warnings, null);
        }

        TrainingDTO dto = new TrainingDTO().setTraining(next).setTrainingCategory(category);
        AgentManagerCalls.run(() -> adminTrainingManager.updateTraining(dto));

        Map<String, Object> updated = view(trainingEntityService.getById(tid), zone);
        changeLogService.record(caller.getTokenId(), caller.getOperator(), "update_training", "training",
                String.valueOf(tid), "修改训练 " + tid + "：" + String.join(", ", changes.keySet()),
                before, editableView(trainingEntityService.getById(tid), category));
        return AgentProblemService.result(false, "update_training", changes, updated, warnings, null);
    }

    // ================================================================== 训练中的题目

    public Map<String, Object> addProblems(long tid, AgentBody body, AgentCaller caller, ZoneId zone) {
        Training training = requireTraining(tid);
        List<AgentBody> items = body.objectList("problems", PROBLEM_ITEM_FIELDS, MAX_PROBLEMS_PER_CALL);
        if (items == null || items.isEmpty()) {
            throw AgentApiException.badRequest("请提供 problems，如 [{\"problem_id\":\"P1001\"}]");
        }
        List<String> warnings = new ArrayList<>();
        List<PlannedProblem> planned = planProblems(loadProblems(tid), items, warnings);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("added", plannedView(planned));
        if (caller.isDryRun()) {
            return AgentProblemService.result(true, "add_training_problems", changes, null, warnings, null);
        }

        insertProblems(tid, planned);
        Map<String, Object> updated = view(trainingEntityService.getById(tid), zone);
        changeLogService.record(caller.getTokenId(), caller.getOperator(), "add_training_problems", "training",
                String.valueOf(tid), "训练 " + tid + " " + training.getTitle() + " 添加题目："
                        + planned.stream().map(p -> p.problem.getProblemId()).collect(Collectors.joining(", ")),
                null, changes.get("added"));
        return AgentProblemService.result(false, "add_training_problems", changes, updated, warnings, null);
    }

    public Map<String, Object> updateProblem(long tid, String problemId, AgentBody body, AgentCaller caller,
                                             ZoneId zone) {
        requireTraining(tid);
        Problem problem = problemService.requireProblem(problemId);
        TrainingProblem current = requireTrainingProblem(tid, problem);
        TrainingProblem next = new TrainingProblem();
        BeanUtil.copyProperties(current, next);
        Integer rank = body.integer("rank", 0, 1_000_000);
        if (rank == null) {
            throw AgentApiException.badRequest("请提供 rank（训练中的排序，升序）");
        }
        next.setRank(rank);
        Map<String, Object> before = trainingProblemView(current, problem);
        Map<String, Object> after = trainingProblemView(next, problem);
        Map<String, Object> changes = AgentViews.diff(before, after);
        if (caller.isDryRun() || changes.isEmpty()) {
            Map<String, Object> response = AgentProblemService.result(caller.isDryRun(), "update_training_problem",
                    changes, null, null, null);
            if (changes.isEmpty()) {
                response.put("changed", false);
            }
            return response;
        }
        AgentManagerCalls.run(() -> adminTrainingProblemManager.updateProblem(
                new TrainingProblem().setId(next.getId()).setRank(next.getRank())));
        touchTraining(tid);
        changeLogService.record(caller.getTokenId(), caller.getOperator(), "update_training_problem", "training",
                String.valueOf(tid), "训练 " + tid + " 修改题目 " + problem.getProblemId() + "："
                        + String.join(", ", changes.keySet()), before, after);
        return AgentProblemService.result(false, "update_training_problem", changes,
                view(trainingEntityService.getById(tid), zone), null, null);
    }

    /** 只把题目移出训练（不会删除题目本身） */
    public Map<String, Object> removeProblem(long tid, String problemId, AgentCaller caller, ZoneId zone) {
        Training training = requireTraining(tid);
        Problem problem = problemService.requireProblem(problemId);
        TrainingProblem current = requireTrainingProblem(tid, problem);
        Map<String, Object> removed = trainingProblemView(current, problem);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("removed", removed);
        List<String> warnings = Collections.singletonList("只是把题目移出训练，题目本身与学生的提交记录都会保留；"
                + "该训练中这道题的完成进度不再计入训练");
        if (caller.isDryRun()) {
            return AgentProblemService.result(true, "remove_training_problem", changes, null, warnings, null);
        }
        Long trainingId = Objects.requireNonNull(training.getId());
        // tid 必须非空：AdminTrainingProblemManager.deleteProblem 在 tid 为 null 时会删除题目本身
        AgentManagerCalls.run(() -> adminTrainingProblemManager.deleteProblem(problem.getId(), trainingId));
        changeLogService.record(caller.getTokenId(), caller.getOperator(), "remove_training_problem", "training",
                String.valueOf(tid), "训练 " + tid + " " + training.getTitle() + " 移除题目 " + problem.getProblemId(),
                removed, null);
        return AgentProblemService.result(false, "remove_training_problem", changes,
                view(trainingEntityService.getById(tid), zone), warnings, null);
    }

    /** 按给定顺序重排训练中的全部题目，rank 依次设为 1..n */
    public Map<String, Object> reorder(long tid, AgentBody body, AgentCaller caller, ZoneId zone) {
        requireTraining(tid);
        List<String> order = body.stringList("problem_ids", 5000);
        if (order == null || order.isEmpty()) {
            throw AgentApiException.badRequest("请提供 problem_ids：训练中全部题目的题号，按新的顺序排列");
        }
        List<Map<String, Object>> existing = loadProblems(tid);
        Map<String, Map<String, Object>> byProblemId = new LinkedHashMap<>();
        for (Map<String, Object> row : existing) {
            byProblemId.put(String.valueOf(row.get("problem_id")).toUpperCase(), row);
        }
        List<String> normalized = order.stream().map(String::toUpperCase).collect(Collectors.toList());
        Set<String> missing = new java.util.TreeSet<>(byProblemId.keySet());
        missing.removeAll(normalized);
        Set<String> unknown = new java.util.TreeSet<>(normalized);
        unknown.removeAll(byProblemId.keySet());
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw AgentApiException.badRequest("problem_ids 必须恰好包含训练中的全部题目"
                    + (missing.isEmpty() ? "" : "；缺少: " + String.join(", ", missing))
                    + (unknown.isEmpty() ? "" : "；不在训练中: " + String.join(", ", unknown)));
        }
        List<String> before = existing.stream().map(row -> String.valueOf(row.get("problem_id")))
                .collect(Collectors.toList());
        Map<String, Object> changes = new LinkedHashMap<>();
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("before", before);
        change.put("after", normalized.stream().map(id -> String.valueOf(byProblemId.get(id).get("problem_id")))
                .collect(Collectors.toList()));
        changes.put("order", change);
        if (caller.isDryRun()) {
            return AgentProblemService.result(true, "reorder_training_problems", changes, null, null, null);
        }
        for (int i = 0; i < normalized.size(); i++) {
            Object tpid = byProblemId.get(normalized.get(i)).get("training_problem_id");
            trainingProblemEntityService.update(new UpdateWrapper<TrainingProblem>()
                    .set("`rank`", i + 1).eq("id", tpid).eq("tid", tid));
        }
        touchTraining(tid);
        changeLogService.record(caller.getTokenId(), caller.getOperator(), "reorder_training_problems", "training",
                String.valueOf(tid), "训练 " + tid + " 调整题目顺序", change.get("before"), change.get("after"));
        return AgentProblemService.result(false, "reorder_training_problems", changes,
                view(trainingEntityService.getById(tid), zone), null, null);
    }

    // ================================================================== 内部

    private static final class PlannedProblem {
        final Problem problem;
        final String displayId;
        final int rank;

        PlannedProblem(Problem problem, String displayId, int rank) {
            this.problem = problem;
            this.displayId = displayId;
            this.rank = rank;
        }
    }

    private List<PlannedProblem> planProblems(List<Map<String, Object>> existing, List<AgentBody> items,
                                              List<String> warnings) {
        Set<Long> pids = new HashSet<>();
        int maxRank = 0;
        for (Map<String, Object> row : existing) {
            pids.add(((Number) row.get("pid")).longValue());
            Object rank = row.get("rank");
            if (rank instanceof Number) {
                maxRank = Math.max(maxRank, ((Number) rank).intValue());
            }
        }
        List<PlannedProblem> planned = new ArrayList<>();
        if (items == null) {
            return planned;
        }
        for (AgentBody item : items) {
            Problem problem = problemService.requireProblem(item.requiredString("problem_id"));
            if (Boolean.TRUE.equals(problem.getIsGroup())) {
                throw new AgentApiException(403, "not_supported", "团队题目 " + problem.getProblemId() + " 不能加入公开训练");
            }
            if (!pids.add(problem.getId())) {
                throw new AgentApiException(409, "conflict", "题目 " + problem.getProblemId() + " 已在该训练中（或在请求中重复）");
            }
            String displayId = problem.getProblemId();
            Integer rank = item.integer("rank", 0, 1_000_000);
            if (rank == null) {
                rank = ++maxRank;
            } else {
                maxRank = Math.max(maxRank, rank);
            }
            if (!Integer.valueOf(1).equals(problem.getAuth())) {
                warnings.add("题目 " + problem.getProblemId() + " 当前为" + ("private".equals(AgentViews.problemAuth(problem.getAuth()))
                        ? "隐藏" : "比赛") + "题目，学生在训练中可能无法查看或提交；需要时把题目 auth 改为 public");
            }
            planned.add(new PlannedProblem(problem, displayId, rank));
        }
        return planned;
    }

    private void insertProblems(long tid, List<PlannedProblem> planned) {
        for (PlannedProblem item : planned) {
            TrainingProblemDTO dto = new TrainingProblemDTO();
            dto.setTid(tid);
            dto.setPid(item.problem.getId());
            dto.setDisplayId(item.displayId);
            AgentManagerCalls.run(() -> adminTrainingProblemManager.addProblemFromPublic(dto));
            trainingProblemEntityService.update(new UpdateWrapper<TrainingProblem>()
                    .set("`rank`", item.rank).eq("tid", tid).eq("pid", item.problem.getId()));
        }
    }

    private static List<Map<String, Object>> plannedView(List<PlannedProblem> planned) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlannedProblem item : planned) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("problem_id", item.problem.getProblemId());
            row.put("title", item.problem.getTitle());
            row.put("display_id", item.displayId);
            row.put("rank", item.rank);
            row.put("auth", AgentViews.problemAuth(item.problem.getAuth()));
            rows.add(row);
        }
        return rows;
    }

    private void applyFields(Training training, AgentBody body) {
        if (body.has("title")) {
            training.setTitle(body.requiredString("title").trim());
        }
        if (body.has("description")) {
            String description = body.string("description");
            training.setDescription(description == null ? "" : description);
        }
        if (body.has("auth")) {
            training.setAuth(body.choice("auth", "Public", "Private"));
            if (training.getAuth() == null) {
                throw AgentApiException.badRequest("auth 只能是 Public 或 Private");
            }
        }
        if (body.has("private_pwd")) {
            training.setPrivatePwd(body.string("private_pwd"));
        }
        if (body.has("rank")) {
            training.setRank(body.integer("rank", -1_000_000, 1_000_000));
        }
        if (body.has("status")) {
            Boolean status = body.bool("status");
            if (status == null) {
                throw AgentApiException.badRequest("status 只能是 true（启用）或 false（停用）");
            }
            training.setStatus(status);
        }
        if ("Private".equals(training.getAuth())
                && (training.getPrivatePwd() == null || training.getPrivatePwd().trim().isEmpty())) {
            throw AgentApiException.badRequest("私有训练（auth=Private）必须设置 private_pwd");
        }
        if (training.getTitle() == null || training.getTitle().isEmpty()) {
            throw AgentApiException.badRequest("训练标题不能为空");
        }
    }

    /** 分类按名称匹配（只匹配全站分类，不含团队分类）；不存在时新建 */
    private TrainingCategory resolveCategory(String name, boolean required, List<String> warnings) {
        if (name == null || name.trim().isEmpty()) {
            if (required) {
                throw AgentApiException.badRequest("请提供 category（训练分类名称，可用 GET /api/agent/v1/admin/meta 查看已有分类）");
            }
            return null;
        }
        String trimmed = name.trim();
        TrainingCategory existed = trainingCategoryEntityService.getOne(new QueryWrapper<TrainingCategory>()
                .eq("name", trimmed).isNull("gid"), false);
        if (existed != null) {
            return existed;
        }
        warnings.add("将新建训练分类: " + trimmed);
        return new TrainingCategory().setName(trimmed).setColor("#409EFF");
    }

    private TrainingCategory currentCategory(long tid) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.id, c.name, c.color FROM mapping_training_category m "
                        + "JOIN training_category c ON c.id = m.cid WHERE m.tid = ? LIMIT 1", tid);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new TrainingCategory().setId(((Number) row.get("id")).longValue())
                .setName((String) row.get("name")).setColor((String) row.get("color"));
    }

    Training requireTraining(long tid) {
        Training training = trainingEntityService.getById(tid);
        if (training == null) {
            throw AgentApiException.notFound("训练不存在: " + tid);
        }
        if (Boolean.TRUE.equals(training.getIsGroup())) {
            throw new AgentApiException(403, "not_supported", "团队训练不支持通过接口管理，请在团队管理中操作");
        }
        return training;
    }

    private TrainingProblem requireTrainingProblem(long tid, Problem problem) {
        TrainingProblem trainingProblem = trainingProblemEntityService.getOne(new QueryWrapper<TrainingProblem>()
                .eq("tid", tid).eq("pid", problem.getId()), false);
        if (trainingProblem == null) {
            throw AgentApiException.notFound("题目 " + problem.getProblemId() + " 不在训练 " + tid + " 中");
        }
        return trainingProblem;
    }

    private void touchTraining(long tid) {
        trainingEntityService.update(new UpdateWrapper<Training>().set("gmt_modified", new Date()).eq("id", tid));
    }

    private List<Map<String, Object>> loadProblems(long tid) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT tp.id AS training_problem_id, tp.pid, p.problem_id, p.title, tp.display_id, "
                        + "tp.`rank` AS `rank`, p.auth, p.type, p.difficulty "
                        + "FROM training_problem tp JOIN problem p ON p.id = tp.pid "
                        + "WHERE tp.tid = ? ORDER BY tp.`rank` ASC, tp.id ASC", tid);
        for (Map<String, Object> row : rows) {
            Object auth = row.get("auth");
            row.put("auth", auth instanceof Number ? AgentViews.problemAuth(((Number) auth).intValue()) : auth);
            Object type = row.get("type");
            row.put("type", type instanceof Number ? AgentViews.problemType(((Number) type).intValue()) : type);
        }
        return rows;
    }

    private Map<String, Object> view(Training training, ZoneId zone) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", training.getId());
        view.putAll(editableView(training, currentCategory(training.getId())));
        view.put("author", training.getAuthor());
        List<Map<String, Object>> problems = loadProblems(training.getId());
        view.put("problem_count", problems.size());
        view.put("problems", problems);
        view.put("version", AgentViews.version(training.getGmtModified()));
        view.put("gmt_create", AgentViews.time(training.getGmtCreate(), zone));
        view.put("gmt_modified", AgentViews.time(training.getGmtModified(), zone));
        return view;
    }

    private static Map<String, Object> editableView(Training training, TrainingCategory category) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("title", training.getTitle());
        view.put("description", training.getDescription());
        view.put("auth", training.getAuth());
        view.put("private_pwd", training.getPrivatePwd());
        view.put("rank", training.getRank());
        view.put("status", training.getStatus());
        view.put("category", category == null ? null
                : category(category.getId(), category.getName(), category.getColor()));
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : EDITABLE_VIEW_KEYS) {
            result.put(key, view.get(key));
        }
        return result;
    }

    private static Map<String, Object> trainingProblemView(TrainingProblem trainingProblem, Problem problem) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("problem_id", problem.getProblemId());
        view.put("title", problem.getTitle());
        view.put("display_id", trainingProblem.getDisplayId());
        view.put("rank", trainingProblem.getRank());
        return view;
    }

    private static Map<String, Object> category(Long id, String name, String color) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("id", id);
        category.put("name", name);
        category.put("color", color);
        return category;
    }
}
