package top.hcode.hoj.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 仅在显式开启启动时数据库初始化时创建 AI 数据接口所需的表；
 * 生产环境请执行 sqlAndsetting/hoj-agent-api-update.sql（含 judge 索引）与 hoj-agent-manage-update.sql。
 */
@Component
@ConditionalOnProperty(
        name = "startup-database-initialization-enabled",
        havingValue = "true")
@Slf4j(topic = "hoj")
public class AgentApiSchemaInitializer implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `agent_access_token` (" +
                        "`id` bigint unsigned NOT NULL AUTO_INCREMENT," +
                        "`token_name` varchar(80) NOT NULL," +
                        "`token_prefix` varchar(20) NOT NULL," +
                        "`token_hash` char(64) NOT NULL," +
                        "`scopes` varchar(100) NOT NULL DEFAULT 'read'," +
                        "`expires_at` datetime DEFAULT NULL," +
                        "`revoked` tinyint(1) NOT NULL DEFAULT 0," +
                        "`created_by` varchar(32) NOT NULL," +
                        "`last_used_at` datetime DEFAULT NULL," +
                        "`last_used_ip` varchar(64) DEFAULT NULL," +
                        "`gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "`gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (`id`)," +
                        "UNIQUE KEY `uk_agent_token_hash` (`token_hash`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `agent_access_log` (" +
                        "`id` bigint unsigned NOT NULL AUTO_INCREMENT," +
                        "`token_id` bigint unsigned NOT NULL," +
                        "`endpoint` varchar(200) NOT NULL," +
                        "`query_string` varchar(2000) DEFAULT NULL," +
                        "`result_count` int DEFAULT NULL," +
                        "`status_code` int NOT NULL," +
                        "`ip` varchar(64) DEFAULT NULL," +
                        "`gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (`id`)," +
                        "KEY `idx_agent_log_token` (`token_id`,`gmt_create`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        Integer scopesColumn = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'agent_access_token' AND column_name = 'scopes'", Integer.class);
        if (scopesColumn == null || scopesColumn == 0) {
            jdbcTemplate.execute("ALTER TABLE agent_access_token "
                    + "ADD COLUMN `scopes` varchar(100) NOT NULL DEFAULT 'read' AFTER `token_hash`");
        }
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `agent_change_log` (" +
                        "`id` bigint unsigned NOT NULL AUTO_INCREMENT," +
                        "`token_id` bigint unsigned NOT NULL," +
                        "`operator_uid` varchar(32) DEFAULT NULL," +
                        "`operator_username` varchar(100) DEFAULT NULL," +
                        "`action` varchar(50) NOT NULL," +
                        "`target_type` varchar(20) NOT NULL," +
                        "`target_id` varchar(64) DEFAULT NULL," +
                        "`summary` varchar(500) DEFAULT NULL," +
                        "`before_json` mediumtext," +
                        "`after_json` mediumtext," +
                        "`gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (`id`)," +
                        "KEY `idx_agent_change_target` (`target_type`,`target_id`)," +
                        "KEY `idx_agent_change_token` (`token_id`,`gmt_create`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        log.info("[Agent API] Schema is ready.");
    }
}
