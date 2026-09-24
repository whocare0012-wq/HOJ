package top.hcode.hoj.schedule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import top.hcode.hoj.dao.user.*;
import top.hcode.hoj.pojo.entity.user.UserInfo;
import top.hcode.hoj.utils.RedisUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CodeforcesRatingSyncTest {
    CodeforcesClient client = mock(CodeforcesClient.class);
    UserInfoEntityService users = mock(UserInfoEntityService.class);
    UserRecordEntityService records = mock(UserRecordEntityService.class);
    RedisUtils redis = new RedisUtils();
    RedisTemplate<String, Object> template = mock(RedisTemplate.class);
    ValueOperations<String, Object> values = mock(ValueOperations.class);
    Object previousTemplate;
    FakeTimeSync sync = new FakeTimeSync();
    static class FakeTimeSync extends CodeforcesRatingSync {
        long nanos;
        boolean interruptOnSleep;
        @Override protected long nanoTime() { return nanos; }
        @Override protected void sleep(long millis) throws InterruptedException {
            if (interruptOnSleep) throw new InterruptedException();
            nanos += millis * 1000000;
        }
    }
    @BeforeEach void setup() {
        previousTemplate = ReflectionTestUtils.getField(RedisUtils.class, "redisTemplate");
        when(template.opsForValue()).thenReturn(values);
        redis.setRedisTemplate(template);
        ReflectionTestUtils.setField(sync, "client", client);
        ReflectionTestUtils.setField(sync, "users", users);
        ReflectionTestUtils.setField(sync, "records", records);
        ReflectionTestUtils.setField(sync, "redis", redis);
    }
    @AfterEach void clearInterrupt() {
        Thread.interrupted();
        ReflectionTestUtils.setField(RedisUtils.class, "redisTemplate", previousTemplate);
    }
    UserInfo user(String id, String handle) { return new UserInfo().setUuid(id).setCfUsername(handle); }

    @Test void deduplicatesAndTrimsHandles() throws Exception {
        when(users.list(any())).thenReturn(Arrays.asList(user("1", " Tourist "), user("2", "tourist"), user("3", "  ")));
        when(client.fetchRating(eq("tourist"), anyInt())).thenReturn(2000);
        when(records.update(any())).thenReturn(true);
        Map<String,Object> report = sync.sync();
        assertEquals(2, report.get("updated"));
        assertEquals(1, report.get("skipped"));
        verify(client, times(1)).fetchRating(eq("tourist"), anyInt());
        verify(records, times(2)).update(any());
    }

    @Test void doesNotRetryPermanentErrorsOrClearOldRatings() throws Exception {
        when(users.list(any())).thenReturn(Collections.singletonList(user("1", "gone")));
        when(client.fetchRating(anyString(), anyInt())).thenThrow(new CodeforcesClient.FetchFailure("http_400", false));
        Map<String,Object> report = sync.sync();
        assertEquals("partial_failure", report.get("state"));
        assertEquals(1, report.get("failed"));
        verify(client, times(1)).fetchRating(anyString(), anyInt());
        verifyNoInteractions(records);
        verify(values, never()).set(eq(CodeforcesRatingSync.SUCCESS_KEY), any());
    }

    @Test void retriesTransientFailureThenSavesSuccessfulRating() throws Exception {
        when(users.list(any())).thenReturn(Collections.singletonList(user("1", "tourist")));
        when(client.fetchRating(anyString(), anyInt())).thenThrow(new CodeforcesClient.FetchFailure("http_503", true)).thenReturn(2000);
        when(records.update(any())).thenReturn(true);
        assertEquals(1, sync.sync().get("updated"));
        verify(client, times(2)).fetchRating(anyString(), anyInt());
        verify(values).set(eq(CodeforcesRatingSync.SUCCESS_KEY), any());
    }

    @Test void stopsAfterRepeatedUpstreamFailures() throws Exception {
        List<UserInfo> list = new ArrayList<>();
        for (int i=0; i<9; i++) list.add(user(""+i, "user"+i));
        when(users.list(any())).thenReturn(list);
        when(client.fetchRating(anyString(), anyInt())).thenThrow(new CodeforcesClient.FetchFailure("http_503", true));
        Map<String,Object> report = sync.sync();
        assertEquals("upstream_unavailable", report.get("state"));
        assertEquals(5, report.get("failed"));
        assertEquals(4, report.get("deferred"));
        verify(client, times(15)).fetchRating(anyString(), anyInt());
        verifyNoInteractions(records);
    }

    @Test void boundsRuntimeAndPreservesInterruption() {
        when(users.list(any())).thenReturn(Collections.singletonList(user("1", "tourist")));
        ReflectionTestUtils.setField(sync, "budgetMillis", 1L);
        assertEquals("budget_exhausted", sync.sync().get("state"));
        verifyNoInteractions(client);
        ReflectionTestUtils.setField(sync, "budgetMillis", 600000L);
        sync.interruptOnSleep = true;
        Map<String,Object> report = sync.sync();
        assertEquals("interrupted", report.get("state"));
        assertEquals(1, report.get("deferred"));
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
