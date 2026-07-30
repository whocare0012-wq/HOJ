package top.hcode.hoj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import top.hcode.hoj.pojo.entity.user.UserAcproblem;


/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Himit_ZH
 * @since 2020-10-23
 */
@Mapper
@Repository
public interface UserAcproblemMapper extends BaseMapper<UserAcproblem> {

    @Insert("INSERT IGNORE INTO user_acproblem " +
            "(uid, pid, submit_id, gmt_create, gmt_modified) " +
            "VALUES (#{uid}, #{pid}, #{submitId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
    int insertIfAbsent(@Param("uid") String uid,
                       @Param("pid") Long pid,
                       @Param("submitId") Long submitId);
}
