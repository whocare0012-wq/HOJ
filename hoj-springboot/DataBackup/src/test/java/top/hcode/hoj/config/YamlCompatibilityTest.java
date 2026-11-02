package top.hcode.hoj.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import top.hcode.hoj.pojo.bo.HydroConfigYamlBO;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SnakeYAML 升级到 2.x 后的兼容性与安全性检查。
 */
class YamlCompatibilityTest {

    @Test
    void springBootLoadsProjectYamlFiles() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String file : new String[]{
                "application.yml", "application-dev.yml", "application-prod.yml",
                "bootstrap.yml", "email-rule.yml"}) {
            List<PropertySource<?>> sources = loader.load(file, new ClassPathResource(file));
            assertFalse(sources.isEmpty(), file);
        }
    }

    @Test
    void hydroConfigStillBindsToBean() {
        String config = "type: default\n"
                + "time: 1s\n"
                + "memory: 256m\n"
                + "langs: [cc, py3]\n"
                + "subtasks:\n"
                + "  - score: 50\n"
                + "    cases:\n"
                + "      - input: 1.in\n"
                + "        output: 1.out\n";
        HydroConfigYamlBO bo = new Yaml().loadAs(config, HydroConfigYamlBO.class);
        assertEquals("1s", bo.getTime());
        assertEquals(2, bo.getLangs().size());
        assertEquals(50, bo.getSubtasks().get(0).get("score"));
    }

    @Test
    void dumpAndLoadRoundTrip() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        HydroConfigYamlBO bo = new HydroConfigYamlBO();
        bo.setType("interactive");
        bo.setMemory("512m");
        String dumped = new Yaml(options).dumpAsMap(bo);
        HydroConfigYamlBO loaded = new Yaml().loadAs(dumped, HydroConfigYamlBO.class);
        assertEquals("interactive", loaded.getType());
        assertEquals("512m", loaded.getMemory());
    }

    @Test
    void untrustedYamlCannotInstantiateArbitraryClasses() {
        // 导入的题目压缩包中的 yaml 不可信，全局标签不能再用于实例化任意 Java 类
        assertThrows(Exception.class,
                () -> new Yaml().load("!!java.net.URL [\"http://example.com/\"]"));
        assertThrows(Exception.class,
                () -> new Yaml().loadAs("subtasks:\n  - score: !!java.net.URL [\"http://example.com/\"]\n",
                        HydroConfigYamlBO.class));

        Map<String, Object> plain = new Yaml().load("title: A+B\ntag: [math]\n");
        assertNotNull(plain);
        assertTrue(plain.containsKey("title"));
    }
}
