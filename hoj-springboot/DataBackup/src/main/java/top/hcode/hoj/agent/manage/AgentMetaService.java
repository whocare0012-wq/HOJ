package top.hcode.hoj.agent.manage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.vo.ProblemDifficultyVO;
import top.hcode.hoj.service.oj.ProblemDifficultyService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写题目、训练前需要知道的可选值：语言、标签、训练分类、难度，以及各枚举字段的取值说明。
 */
@Service
public class AgentMetaService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ProblemDifficultyService problemDifficultyService;

    public Map<String, Object> meta() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("languages", jdbcTemplate.queryForList(
                "SELECT name, description, is_spj AS can_be_spj_language FROM language WHERE oj = ? "
                        + "ORDER BY seq DESC, id ASC", AgentProblemService.OJ));
        body.put("tags", jdbcTemplate.queryForList(
                "SELECT t.name, t.color, COUNT(pt.pid) AS problem_count FROM tag t "
                        + "LEFT JOIN problem_tag pt ON pt.tid = t.id "
                        + "WHERE t.oj = ? AND t.gid IS NULL GROUP BY t.id, t.name, t.color ORDER BY t.id",
                AgentProblemService.OJ));
        body.put("training_categories", jdbcTemplate.queryForList(
                "SELECT id, name, color FROM training_category WHERE gid IS NULL ORDER BY id"));

        List<Map<String, Object>> difficulties = new ArrayList<>();
        CommonResult<List<ProblemDifficultyVO>> result = problemDifficultyService.getProblemDifficulties();
        if (result != null && result.getData() != null) {
            for (ProblemDifficultyVO vo : result.getData()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("difficulty", vo.getDifficultyValue());
                row.put("name", vo.getDisplayText());
                row.put("problem_count", vo.getProblemCount());
                difficulties.add(row);
            }
        }
        body.put("difficulties", difficulties);

        Map<String, Object> enums = new LinkedHashMap<>();
        enums.put("problem.type", Arrays.asList("ACM", "OI"));
        enums.put("problem.auth", Arrays.asList("public（公开）", "private（隐藏）", "contest（比赛题目，只读）"));
        enums.put("problem.judge_mode", Arrays.asList("default（普通判题）", "spj（特殊判题）", "interactive（交互判题）"));
        enums.put("problem.judge_case_mode", Arrays.asList(
                "default（ACM/OI 通用）", "ergodic_without_error（遇错止评，仅 ACM）",
                "subtask_lowest（子任务取最低分，仅 OI）", "subtask_average（子任务取平均分，仅 OI）"));
        enums.put("training.auth", Arrays.asList("Public", "Private（需要 private_pwd）"));
        body.put("enums", enums);
        return body;
    }
}
