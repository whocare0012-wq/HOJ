package top.hcode.hoj.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.hcode.hoj.utils.RedisUtils;

import javax.annotation.Resource;

/**
 * 每个令牌每分钟的请求次数限制（Redis 固定窗口计数）。Redis 不可用时放行，只记录日志。
 */
@Component
@Slf4j(topic = "hoj")
public class AgentRateLimiter {

    @Resource
    private RedisUtils redisUtils;

    @Value("${hoj.agent-api.rate-limit-per-minute:120}")
    private int limitPerMinute;

    public int getLimitPerMinute() {
        return limitPerMinute;
    }

    /** 计入一次请求；超出本分钟限额时返回 false */
    public boolean tryAcquire(long tokenId) {
        if (limitPerMinute <= 0) {
            return true;
        }
        String key = "agent_api_rate:" + tokenId + ":" + (System.currentTimeMillis() / 60_000L);
        try {
            long count = redisUtils.incr(key, 1);
            if (count == 1) {
                redisUtils.expire(key, 120);
            }
            return count <= limitPerMinute;
        } catch (Exception e) {
            log.warn("[Agent API] Rate limit check failed, request allowed", e);
            return true;
        }
    }
}
