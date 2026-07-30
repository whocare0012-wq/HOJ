package top.hcode.hoj.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.hcode.hoj.common.DailyFortuneType;

import javax.annotation.Resource;

/**
 * Additive schema upgrade for configurable daily-fortune advice.
 *
 * The statement never drops or alters an existing business table, so it is
 * safe for databases created by older HOJ releases.
 */
@Component
@ConditionalOnProperty(
        name = "startup-database-initialization-enabled",
        havingValue = "true")
@Slf4j(topic = "hoj")
public class DailyFortuneAdviceSchemaInitializer implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `daily_fortune_config` (" +
                        "`id` bigint(20) unsigned NOT NULL AUTO_INCREMENT," +
                        "`code` varchar(32) NOT NULL," +
                        "`name` varchar(32) NOT NULL," +
                        "`color` varchar(7) NOT NULL DEFAULT '#409EFF'," +
                        "`description` varchar(255) DEFAULT NULL," +
                        "`sort_order` int(11) NOT NULL DEFAULT 0," +
                        "`enabled` tinyint(1) NOT NULL DEFAULT 1," +
                        "`legacy_score` int(11) NOT NULL DEFAULT 64," +
                        "`gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "`gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (`id`)," +
                        "UNIQUE KEY `uk_daily_fortune_code` (`code`)," +
                        "KEY `idx_daily_fortune_enabled_sort` (`enabled`,`sort_order`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `daily_fortune_advice` (" +
                        "`id` bigint(20) unsigned NOT NULL AUTO_INCREMENT," +
                        "`fortune_type` varchar(32) NOT NULL," +
                        "`advice_type` varchar(16) NOT NULL," +
                        "`title` varchar(100) NOT NULL," +
                        "`description` varchar(255) DEFAULT NULL," +
                        "`sort_order` int(11) NOT NULL DEFAULT 0," +
                        "`enabled` tinyint(1) NOT NULL DEFAULT 1," +
                        "`usage_count` bigint(20) unsigned NOT NULL DEFAULT 0," +
                        "`gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "`gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (`id`)," +
                        "KEY `idx_fortune_advice_type` (`fortune_type`, `advice_type`, `sort_order`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        addColumnIfMissing(
                "daily_fortune_advice",
                "enabled",
                "ALTER TABLE `daily_fortune_advice` " +
                        "ADD COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 AFTER `sort_order`");
        addColumnIfMissing(
                "daily_fortune_advice",
                "usage_count",
                "ALTER TABLE `daily_fortune_advice` " +
                        "ADD COLUMN `usage_count` bigint(20) unsigned NOT NULL DEFAULT 0 " +
                        "AFTER `enabled`");
        seedDefaultFortunes();
        log.info("[Daily Fortune] Fortune and advice configuration schema is ready.");
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName);
        if (count != null && count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void seedDefaultFortunes() {
        Integer existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_fortune_config",
                Integer.class);
        if (existingCount != null && existingCount > 0) {
            return;
        }
        for (DailyFortuneType type : DailyFortuneType.values()) {
            jdbcTemplate.update(
                    "INSERT INTO daily_fortune_config " +
                            "(code, name, color, description, sort_order, enabled, " +
                            "legacy_score, gmt_create, gmt_modified) " +
                            "VALUES (?, ?, ?, ?, ?, 1, ?, NOW(), NOW())",
                    type.getCode(),
                    type.getDisplayName(),
                    type.getColor(),
                    type.getDescription(),
                    type.getSortOrder(),
                    type.getLegacyScore());
        }
    }
}
