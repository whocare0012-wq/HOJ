package top.hcode.hoj.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Additive schema upgrade for the frontend learning-resource library.
 */
@Component
@ConditionalOnProperty(
        name = "startup-database-initialization-enabled",
        havingValue = "true")
@Slf4j(topic = "hoj")
public class LearningResourceSchemaInitializer implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `learning_resource_folder` (" +
                        "`id` bigint(20) unsigned NOT NULL AUTO_INCREMENT," +
                        "`name` varchar(60) NOT NULL," +
                        "`color` varchar(7) NOT NULL DEFAULT '#409EFF'," +
                        "`creator_uid` varchar(32) NOT NULL," +
                        "`creator_username` varchar(64) NOT NULL," +
                        "`sort_order` int(11) NOT NULL DEFAULT 0," +
                        "`gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "`gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (`id`)," +
                        "UNIQUE KEY `uk_learning_resource_folder_name` (`name`)," +
                        "KEY `idx_learning_resource_folder_sort` (`sort_order`,`id`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `learning_resource_file` (" +
                        "`id` bigint(20) unsigned NOT NULL AUTO_INCREMENT," +
                        "`folder_id` bigint(20) unsigned NOT NULL," +
                        "`original_name` varchar(255) NOT NULL," +
                        "`stored_name` varchar(100) NOT NULL," +
                        "`content_type` varchar(150) NOT NULL DEFAULT 'application/octet-stream'," +
                        "`file_size` bigint(20) unsigned NOT NULL DEFAULT 0," +
                        "`uploader_uid` varchar(32) NOT NULL," +
                        "`uploader_username` varchar(64) NOT NULL," +
                        "`gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "`gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (`id`)," +
                        "KEY `idx_learning_resource_file_folder` (`folder_id`,`gmt_create`,`id`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        log.info("[Learning Resource] Schema is ready.");
    }
}
