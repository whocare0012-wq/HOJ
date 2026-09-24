package top.hcode.hoj.crawler.problem;

import lombok.extern.slf4j.Slf4j;

/**
 * @Author: Himit_ZH
 * @Date: 2021/2/17 22:40
 * @Description:
 */
@Slf4j(topic = "hoj")
public class ProblemContext {

    ProblemStrategy problemStrategy;

    public ProblemContext(ProblemStrategy problemStrategy) {
        this.problemStrategy = problemStrategy;
    }

    //上下文接口
    public ProblemStrategy.RemoteProblemInfo getProblemInfo(String problemId, String author) throws Exception {
        try {
            return problemStrategy.getProblemInfo(problemId, author);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to get remote problem details: strategy={}, problemId={}, reason={}",
                    problemStrategy.getClass().getSimpleName(), problemId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to get remote problem details: strategy={}, problemId={}, reason={}",
                    problemStrategy.getClass().getSimpleName(), problemId, e.getMessage(), e);
            throw e;
        }
    }

    //上下文接口
    public ProblemStrategy.RemoteProblemInfo getProblemInfoByLogin(String problemId, String author, String username, String password) throws Exception {

        try {
            return problemStrategy.getProblemInfoByLogin(problemId, author, username, password);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to get remote problem details with login: strategy={}, problemId={}, reason={}",
                    problemStrategy.getClass().getSimpleName(), problemId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to get remote problem details with login: strategy={}, problemId={}, reason={}",
                    problemStrategy.getClass().getSimpleName(), problemId, e.getMessage(), e);
        }
        return null;
    }
}
