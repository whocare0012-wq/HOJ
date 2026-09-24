package top.hcode.hoj.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.hcode.hoj.dao.user.UserInfoEntityService;
import top.hcode.hoj.dao.user.UserRecordEntityService;
import top.hcode.hoj.pojo.entity.user.UserInfo;
import top.hcode.hoj.pojo.entity.user.UserRecord;
import top.hcode.hoj.utils.RedisUtils;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j(topic = "hoj")
public class CodeforcesRatingSync {
    public static final String STATUS_KEY = "hoj:schedule:codeforces:last-run";
    public static final String SUCCESS_KEY = "hoj:schedule:codeforces:last-success";
    @Autowired private CodeforcesClient client;
    @Autowired private UserInfoEntityService users;
    @Autowired private UserRecordEntityService records;
    @Autowired private RedisUtils redis;
    @Value("${hoj.codeforces.sync-budget-ms:600000}") private long budgetMillis = 600000;
    private final AtomicBoolean running = new AtomicBoolean();

    @Async("codeforcesSyncExecutor")
    public void runScheduled() { sync(); }

    public Map<String, Object> sync() {
        if (!running.compareAndSet(false, true)) {
            return Collections.singletonMap("state", "already_running");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        long started = nanoTime();
        int updated = 0, failed = 0, skipped = 0, deferred = 0;
        int total = 0, processed = 0;
        String state = "completed";
        try {
            Map<String, List<UserInfo>> handles = new LinkedHashMap<>();
            for (UserInfo user : users.list(new QueryWrapper<UserInfo>()
                    .select("uuid", "cf_username").isNotNull("cf_username"))) {
                String handle = user.getCfUsername() == null ? "" : user.getCfUsername().trim();
                if (handle.isEmpty() || handle.length() > 255 || handle.chars().anyMatch(Character::isISOControl)) {
                    skipped++;
                    continue;
                }
                handles.computeIfAbsent(handle.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(user);
            }
            total = handles.values().stream().mapToInt(List::size).sum();
            int transientFailures = 0;
            boolean requested = false;
            for (Map.Entry<String, List<UserInfo>> entry : handles.entrySet()) {
                if (Thread.currentThread().isInterrupted() || remaining(started) <= 2000 || transientFailures >= 5) {
                    state = Thread.currentThread().isInterrupted() ? "interrupted" : transientFailures >= 5 ? "upstream_unavailable" : "budget_exhausted";
                    deferred = total - processed;
                    break;
                }
                Integer rating = null;
                CodeforcesClient.FetchFailure failure = null;
                boolean fetched = false;
                for (int attempt = 0; attempt < 3; attempt++) {
                    long wait = requested ? (attempt == 2 ? 4000 : 2000) : 0;
                    if (remaining(started) <= wait + 1) break;
                    sleep(wait);
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    int timeout = (int) Math.min(10000, remaining(started));
                    if (timeout <= 0) break;
                    requested = true;
                    try {
                        rating = client.fetchRating(entry.getKey(), timeout);
                        fetched = true;
                        break;
                    } catch (CodeforcesClient.FetchFailure e) {
                        failure = e;
                        if (!e.isRetryable()) break;
                    }
                }
                if (!fetched) {
                    if (failure == null) {
                        state = "budget_exhausted";
                        break;
                    }
                    failed += entry.getValue().size();
                    transientFailures = failure != null && failure.isRetryable() ? transientFailures + 1 : 0;
                    log.warn("Codeforces sync failed: uid={}, userCount={}, reason={}",
                            entry.getValue().get(0).getUuid(), entry.getValue().size(), failure.getMessage());
                } else {
                    transientFailures = 0;
                    for (UserInfo user : entry.getValue()) {
                        try {
                            if (records.update(new UpdateWrapper<UserRecord>().eq("uid", user.getUuid()).set("rating", rating))) {
                                updated++;
                            } else {
                                failed++;
                                log.warn("Codeforces rating persistence failed: uid={}", user.getUuid());
                            }
                        } catch (RuntimeException e) {
                            failed++;
                            log.error("Codeforces rating persistence failed: uid={}, exception={}", user.getUuid(), e.getClass().getSimpleName());
                        }
                    }
                }
                processed += entry.getValue().size();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state = "interrupted";
        } catch (RuntimeException e) {
            state = "failed";
            log.error("Codeforces sync aborted: exception={}", e.getClass().getSimpleName());
        } finally {
            deferred = total - processed;
            if ("completed".equals(state) && failed > 0) state = "partial_failure";
            report.put("state", state);
            report.put("updated", updated);
            report.put("failed", failed);
            report.put("skipped", skipped);
            report.put("deferred", deferred);
            report.put("finishedAt", System.currentTimeMillis());
            report.put("elapsedMillis", TimeUnit.NANOSECONDS.toMillis(nanoTime() - started));
            try {
                redis.set(STATUS_KEY, report);
                if ("completed".equals(state) && failed == 0 && updated > 0 && deferred == 0) {
                    redis.set(SUCCESS_KEY, report.get("finishedAt"));
                }
            } catch (RuntimeException e) {
                log.warn("Unable to persist Codeforces sync summary");
            } finally {
                running.set(false);
            }
            log.info("Codeforces sync summary: {}", report);
        }
        return report;
    }

    protected long nanoTime() { return System.nanoTime(); }
    protected void sleep(long millis) throws InterruptedException { Thread.sleep(millis); }
    private long remaining(long started) {
        return budgetMillis - TimeUnit.NANOSECONDS.toMillis(nanoTime() - started);
    }
}
