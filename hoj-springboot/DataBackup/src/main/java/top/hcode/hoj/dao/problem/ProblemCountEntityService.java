package top.hcode.hoj.dao.problem;

import top.hcode.hoj.pojo.entity.problem.ProblemCount;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Himit_ZH
 * @since 2020-10-23
 */
public interface ProblemCountEntityService {
    ProblemCount getContestProblemCount(Long pid, Long cpid, Long cid);
}
