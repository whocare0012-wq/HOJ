package top.hcode.hoj.agent.manage;

import top.hcode.hoj.shiro.AccountProfile;

/**
 * 一次管理接口调用的上下文：令牌、执行身份（令牌创建者）与是否为演练（dry_run 只校验并返回变更预览，不写入）。
 */
public final class AgentCaller {

    private final long tokenId;
    private final AccountProfile operator;
    private final boolean dryRun;

    public AgentCaller(long tokenId, AccountProfile operator, boolean dryRun) {
        this.tokenId = tokenId;
        this.operator = operator;
        this.dryRun = dryRun;
    }

    public long getTokenId() {
        return tokenId;
    }

    public AccountProfile getOperator() {
        return operator;
    }

    public String getUsername() {
        return operator == null ? null : operator.getUsername();
    }

    public boolean isDryRun() {
        return dryRun;
    }
}
