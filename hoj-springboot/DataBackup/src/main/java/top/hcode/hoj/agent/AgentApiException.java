package top.hcode.hoj.agent;

/**
 * AI 数据接口的错误，带 HTTP 状态码与稳定的机器可读错误码，便于 agent 判断如何处理。
 */
public class AgentApiException extends RuntimeException {

    private final int status;
    private final String code;

    public AgentApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AgentApiException badRequest(String message) {
        return new AgentApiException(400, "invalid_parameter", message);
    }

    public static AgentApiException notFound(String message) {
        return new AgentApiException(404, "not_found", message);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
