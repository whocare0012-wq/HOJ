package top.hcode.hoj.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisUtilsTest {

    private RedisTemplate<String, Object> redisTemplate;
    private RedisUtils redisUtils;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        redisUtils = new RedisUtils();
        redisUtils.setRedisTemplate(redisTemplate);
    }

    @Test
    void deletesSingleKeyDirectly() {
        redisUtils.del("single-key");

        verify(redisTemplate).delete("single-key");
    }

    @Test
    void deletesMultipleKeysAsStringCollection() {
        redisUtils.del("first-key", "second-key");

        verify(redisTemplate).delete(Arrays.asList("first-key", "second-key"));
    }
}
