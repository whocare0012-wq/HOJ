package top.hcode.hoj.agent;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.ThreadState;
import org.apache.shiro.web.subject.WebSubject;
import org.springframework.stereotype.Component;
import top.hcode.hoj.shiro.AccountProfile;
import top.hcode.hoj.shiro.AccountRealm;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * /api/agent/** 在 Shiro 中是 anon，当前线程绑定的是匿名 Subject。管理接口复用后台 Manager，
 * 而这些 Manager 通过 SecurityUtils 读取当前用户、判断角色，因此这里把令牌创建者（root）
 * 构造成已认证的 Subject 绑定到当前线程，请求结束后恢复原 Subject。
 * 角色与权限照常由 {@link AccountRealm} 从数据库（及其缓存）读取。
 */
@Component
public class AgentSubjectBinder {

    @Resource
    private SecurityManager securityManager;

    @Resource
    private AccountRealm accountRealm;

    public ThreadState bind(AccountProfile profile, HttpServletRequest request, HttpServletResponse response) {
        PrincipalCollection principals = new SimplePrincipalCollection(profile, accountRealm.getName());
        Subject subject = new WebSubject.Builder(securityManager, request, response)
                .principals(principals)
                .authenticated(true)
                .sessionCreationEnabled(false)
                .buildSubject();
        ThreadState state = new SubjectThreadState(subject);
        state.bind();
        return state;
    }
}
