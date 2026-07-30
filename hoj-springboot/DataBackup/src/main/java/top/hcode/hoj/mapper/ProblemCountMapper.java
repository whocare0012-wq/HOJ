package top.hcode.hoj.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import top.hcode.hoj.pojo.entity.problem.ProblemCount;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Himit_ZH
 * @since 2020-10-23
 */
@Mapper
@Repository
public interface ProblemCountMapper {
    ProblemCount getContestProblemCount(@Param("pid") Long pid, @Param("cpid") Long cpid, @Param("cid") Long cid);
}
