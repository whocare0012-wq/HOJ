package top.hcode.hoj.agent.manage;

import top.hcode.hoj.agent.AgentApiException;
import top.hcode.hoj.common.exception.StatusFailException;
import top.hcode.hoj.common.exception.StatusForbiddenException;
import top.hcode.hoj.common.exception.StatusSystemErrorException;
import top.hcode.hoj.exception.ProblemIDRepeatException;

/**
 * 调用后台 Manager 时，把它们的业务异常转换成带 HTTP 状态码与错误码的 {@link AgentApiException}。
 */
final class AgentManagerCalls {

    private AgentManagerCalls() {
    }

    @FunctionalInterface
    interface Call<T> {
        T run() throws Exception;
    }

    @FunctionalInterface
    interface VoidCall {
        void run() throws Exception;
    }

    static void run(VoidCall call) {
        call(() -> {
            call.run();
            return null;
        });
    }

    static <T> T call(Call<T> call) {
        try {
            return call.run();
        } catch (AgentApiException e) {
            throw e;
        } catch (StatusForbiddenException e) {
            throw new AgentApiException(403, "forbidden", e.getMessage());
        } catch (StatusFailException e) {
            throw new AgentApiException(400, "operation_failed", e.getMessage());
        } catch (StatusSystemErrorException e) {
            throw new AgentApiException(500, "system_error", e.getMessage());
        } catch (ProblemIDRepeatException e) {
            throw new AgentApiException(409, "conflict", e.getMessage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
