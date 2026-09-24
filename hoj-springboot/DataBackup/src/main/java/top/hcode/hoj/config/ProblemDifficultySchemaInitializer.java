package top.hcode.hoj.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@ConditionalOnProperty(
        name = "startup-database-initialization-enabled",
        havingValue = "true")
@Slf4j(topic = "hoj")
public class ProblemDifficultySchemaInitializer implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `problem_difficulty_config` (" +
                        "`difficulty_value` int unsigned NOT NULL," +
                        "`display_text` varchar(20) NOT NULL," +
                        "`border_color` varchar(7) NOT NULL," +
                        "`base_points` decimal(10,2) NOT NULL DEFAULT 10," +
                        "`sort_order` int unsigned NOT NULL DEFAULT 0," +
                        "`gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "`gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (`difficulty_value`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute(
                "ALTER TABLE problem_difficulty_config " +
                        "MODIFY difficulty_value int unsigned NOT NULL, " +
                        "MODIFY sort_order int unsigned NOT NULL DEFAULT 0");
        jdbcTemplate.update(
                "INSERT IGNORE INTO problem_difficulty_config " +
                        "(difficulty_value, display_text, border_color, sort_order, base_points) VALUES " +
                        "(0, '简单', '#19BE6B', 0, 10), " +
                        "(1, '中等', '#2D8CF0', 1, 20), " +
                        "(2, '困难', '#ED3F14', 2, 40)");
        log.info("[Problem Difficulty] Schema is ready.");
    }
}
