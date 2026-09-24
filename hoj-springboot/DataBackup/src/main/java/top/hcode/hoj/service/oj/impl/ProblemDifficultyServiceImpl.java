package top.hcode.hoj.service.oj.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.dto.ProblemDifficultyConfigDTO;
import top.hcode.hoj.pojo.vo.ProblemDifficultyVO;
import top.hcode.hoj.service.oj.ProblemDifficultyService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProblemDifficultyServiceImpl implements ProblemDifficultyService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource private top.hcode.hoj.service.oj.OjPointsService ojPointsService;

    @Override
    public CommonResult<List<ProblemDifficultyVO>> getProblemDifficulties() {
        return CommonResult.successResponse(listDifficulties());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<List<ProblemDifficultyVO>> updateProblemDifficulties(
            List<ProblemDifficultyConfigDTO> difficulties) {
        try {
            List<Integer> existingValues = jdbcTemplate.queryForList(
                    "SELECT difficulty_value FROM problem_difficulty_config ORDER BY difficulty_value FOR UPDATE",
                    Integer.class);
            validateDifficulties(difficulties, existingValues);

            Set<Integer> submittedValues = new HashSet<>();
            for (ProblemDifficultyConfigDTO difficulty : difficulties) {
                if (difficulty.getDifficultyValue() != null) {
                    submittedValues.add(difficulty.getDifficultyValue());
                }
            }

            List<Integer> removedValues = new ArrayList<>();
            for (Integer existingValue : existingValues) {
                if (!submittedValues.contains(existingValue)) {
                    removedValues.add(existingValue);
                }
            }
            ensureDifficultiesAreUnused(removedValues);

            Integer nextDifficultyValue = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(difficulty_value), -1) + 1 " +
                            "FROM problem_difficulty_config",
                    Integer.class);

            for (Integer removedValue : removedValues) {
                jdbcTemplate.update(
                        "DELETE FROM problem_difficulty_config WHERE difficulty_value = ?",
                        removedValue);
            }

            for (int index = 0; index < difficulties.size(); index++) {
                ProblemDifficultyConfigDTO difficulty = difficulties.get(index);
                if (difficulty.getDifficultyValue() != null) {
                    java.math.BigDecimal before = jdbcTemplate.queryForObject(
                        "SELECT base_points FROM problem_difficulty_config WHERE difficulty_value=?",
                        java.math.BigDecimal.class, difficulty.getDifficultyValue());
                    ojPointsService.logDifficulty(difficulty.getDifficultyValue(),difficulty.getDisplayText(),before,difficulty.getBasePoints());
                }
                String displayText = difficulty.getDisplayText().trim();
                String borderColor = difficulty.getBorderColor().toUpperCase();
                if (difficulty.getDifficultyValue() == null) {
                    difficulty.setDifficultyValue(nextDifficultyValue++);
                    jdbcTemplate.update(
                            "INSERT INTO problem_difficulty_config " +
                                    "(difficulty_value, display_text, border_color, sort_order, base_points) " +
                                    "VALUES (?, ?, ?, ?, ?)",
                            difficulty.getDifficultyValue(),
                            displayText,
                            borderColor,
                            index, difficulty.getBasePoints());
                } else {
                    jdbcTemplate.update(
                            "UPDATE problem_difficulty_config " +
                                    "SET display_text = ?, border_color = ?, sort_order = ?, base_points = ? " +
                                    "WHERE difficulty_value = ?",
                            displayText,
                            borderColor,
                            index, difficulty.getBasePoints(),
                            difficulty.getDifficultyValue());
                }
            }
            return CommonResult.successResponse(listDifficulties());
        } catch (IllegalArgumentException exception) {
            return CommonResult.errorResponse(exception.getMessage());
        }
    }

    private void validateDifficulties(
            List<ProblemDifficultyConfigDTO> difficulties,
            List<Integer> existingValues) {
        if (difficulties == null || difficulties.isEmpty()) {
            throw new IllegalArgumentException("至少需要保留一个难度级别");
        }
        if (difficulties.size() > 50) {
            throw new IllegalArgumentException("最多可设置 50 个难度级别");
        }
        Set<Integer> submittedValues = new HashSet<>();
        Set<String> submittedNames = new LinkedHashSet<>();
        for (ProblemDifficultyConfigDTO difficulty : difficulties) {
            if (difficulty == null) {
                throw new IllegalArgumentException("难度配置不能为空");
            }
            top.hcode.hoj.service.oj.OjPointsService.validatePoints(difficulty.getBasePoints());
            Integer difficultyValue = difficulty.getDifficultyValue();
            if (difficultyValue != null) {
                if (difficultyValue < 0) {
                    throw new IllegalArgumentException("难度编号不能小于 0");
                }
                if (!existingValues.contains(difficultyValue)) {
                    throw new IllegalArgumentException(
                            "难度编号 " + difficultyValue + " 不存在");
                }
                if (!submittedValues.add(difficultyValue)) {
                    throw new IllegalArgumentException("难度编号不能重复");
                }
            }
            String displayText = difficulty.getDisplayText();
            if (!StringUtils.hasText(displayText)) {
                throw new IllegalArgumentException("难度显示文字不能为空");
            }
            if (displayText.trim().length() > 20) {
                throw new IllegalArgumentException("难度显示文字不能超过 20 个字符");
            }
            if (!submittedNames.add(displayText.trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("难度显示文字不能重复");
            }
            if (!StringUtils.hasText(difficulty.getBorderColor())
                    || !HEX_COLOR.matcher(difficulty.getBorderColor()).matches()) {
                throw new IllegalArgumentException("标签颜色必须是 #RRGGBB 格式");
            }
        }
    }

    private void ensureDifficultiesAreUnused(List<Integer> removedValues) {
        for (Integer removedValue : removedValues) {
            Long problemCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM problem WHERE difficulty = ?",
                    Long.class,
                    removedValue);
            if (problemCount != null && problemCount > 0) {
                String displayText = jdbcTemplate.queryForObject(
                        "SELECT display_text FROM problem_difficulty_config " +
                                "WHERE difficulty_value = ?",
                        String.class,
                        removedValue);
                throw new IllegalArgumentException(
                        "难度“" + displayText + "”已被 " + problemCount +
                                " 道题目使用，不能删除");
            }
        }
    }

    private List<ProblemDifficultyVO> listDifficulties() {
        return jdbcTemplate.query(
                "SELECT c.difficulty_value, c.display_text, c.border_color, " +
                        "c.sort_order, c.base_points, COUNT(p.id) AS problem_count " +
                        "FROM problem_difficulty_config c " +
                        "LEFT JOIN problem p ON p.difficulty = c.difficulty_value " +
                        "GROUP BY c.difficulty_value, c.display_text, " +
                        "c.border_color, c.sort_order, c.base_points " +
                        "ORDER BY c.sort_order ASC, c.difficulty_value ASC",
                (resultSet, rowNum) -> {
                    ProblemDifficultyVO difficulty = new ProblemDifficultyVO();
                    difficulty.setDifficultyValue(resultSet.getInt("difficulty_value"));
                    difficulty.setDisplayText(resultSet.getString("display_text"));
                    difficulty.setBorderColor(resultSet.getString("border_color"));
                    difficulty.setBasePoints(resultSet.getBigDecimal("base_points"));
                    difficulty.setSortOrder(resultSet.getInt("sort_order"));
                    difficulty.setProblemCount(resultSet.getLong("problem_count"));
                    return difficulty;
                });
    }
}
