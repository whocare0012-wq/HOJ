package top.hcode.hoj.compatibility;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@Tag("integration")
@Tag("database-compatibility")
class DatabaseCompatibilityTest {

    private static final String MAPPER_PATTERN = "classpath*:top/hcode/hoj/mapper/**/*.class";
    private static final int EXPECTED_PERSISTED_ENTITY_COUNT = 48;

    @Test
    void everyPersistedEntityCanBeSelectedFromTheExistingSchema() throws Exception {
        String url = requiredSetting("hoj.db.compat.url", "HOJ_DB_COMPAT_URL");
        String username = requiredSetting("hoj.db.compat.username", "HOJ_DB_COMPAT_USERNAME");
        String password = setting("hoj.db.compat.password", "HOJ_DB_COMPAT_PASSWORD");

        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", username);
        connectionProperties.setProperty("password", password == null ? "" : password);
        connectionProperties.setProperty("useSSL", "false");

        Set<Class<?>> entityTypes = findPersistedEntityTypes();
        Assertions.assertEquals(EXPECTED_PERSISTED_ENTITY_COUNT, entityTypes.size(),
                "The persisted entity contract changed; review the database compatibility baseline explicitly.");

        List<String> failures = new ArrayList<String>();
        try (Connection connection = DriverManager.getConnection(url, connectionProperties)) {
            connection.setReadOnly(true);
            for (Class<?> entityType : entityTypes) {
                verifyMapping(connection, entityType, failures);
            }
        }

        Assertions.assertTrue(failures.isEmpty(),
                "Existing HOJ schema is incompatible with persisted entity mappings:\n" + joinLines(failures));
    }

    private Set<Class<?>> findPersistedEntityTypes() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
        Set<Class<?>> entityTypes = new LinkedHashSet<Class<?>>();

        for (Resource resource : resolver.getResources(MAPPER_PATTERN)) {
            String filename = resource.getFilename();
            if (!resource.isReadable() || filename == null || !filename.endsWith(".class")) {
                continue;
            }
            ClassMetadata metadata = metadataReaderFactory.getMetadataReader(resource).getClassMetadata();
            if (!metadata.isInterface() || metadata.getClassName().contains("$")) {
                continue;
            }
            Class<?> mapperType = Class.forName(metadata.getClassName());
            if (!BaseMapper.class.isAssignableFrom(mapperType)) {
                continue;
            }
            Class<?> entityType = ResolvableType.forClass(mapperType)
                    .as(BaseMapper.class)
                    .getGeneric(0)
                    .resolve();
            if (entityType != null) {
                entityTypes.add(entityType);
            }
        }
        return entityTypes;
    }

    private void verifyMapping(Connection connection, Class<?> entityType, List<String> failures) {
        try {
            TableInfo tableInfo = tableInfo(entityType);
            List<String> columns = new ArrayList<String>();
            if (tableInfo.getKeyColumn() != null && !tableInfo.getKeyColumn().trim().isEmpty()) {
                columns.add(cleanIdentifier(tableInfo.getKeyColumn()));
            }
            for (TableFieldInfo fieldInfo : tableInfo.getFieldList()) {
                columns.add(cleanIdentifier(fieldInfo.getColumn()));
            }
            Collections.sort(columns, new Comparator<String>() {
                @Override
                public int compare(String left, String right) {
                    return left.compareTo(right);
                }
            });

            String sql = "SELECT " + quoteIdentifiers(columns)
                    + " FROM " + quoteIdentifier(cleanIdentifier(tableInfo.getTableName()))
                    + " WHERE 1 = 0";
            try (Statement statement = connection.createStatement(); ResultSet ignored = statement.executeQuery(sql)) {
                // MySQL parses every mapped identifier without reading or modifying any row.
            }
        } catch (Exception exception) {
            failures.add(entityType.getName() + ": " + rootMessage(exception));
        }
    }

    private TableInfo tableInfo(Class<?> entityType) {
        TableInfo cached = TableInfoHelper.getTableInfo(entityType);
        if (cached != null) {
            return cached;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        configuration.setGlobalConfig(globalConfig);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "database-compatibility");
        assistant.setCurrentNamespace("compatibility." + entityType.getName());
        return TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private String quoteIdentifiers(List<String> identifiers) {
        List<String> quoted = new ArrayList<String>();
        for (String identifier : identifiers) {
            quoted.add(quoteIdentifier(identifier));
        }
        return joinWithComma(quoted);
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String cleanIdentifier(String identifier) {
        return identifier.replace("`", "").trim();
    }

    private String requiredSetting(String propertyName, String environmentName) {
        String value = setting(propertyName, environmentName);
        Assertions.assertTrue(value != null && !value.trim().isEmpty(),
                "Missing database compatibility setting: " + propertyName + " or " + environmentName);
        return value;
    }

    private String setting(String propertyName, String environmentName) {
        String value = System.getProperty(propertyName);
        return value == null ? System.getenv(environmentName) : value;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getName() : current.getMessage();
    }

    private String joinLines(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(" - ").append(value);
        }
        return builder.toString();
    }

    private String joinWithComma(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
