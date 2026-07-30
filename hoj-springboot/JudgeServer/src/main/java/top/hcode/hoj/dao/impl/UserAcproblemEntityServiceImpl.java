package top.hcode.hoj.dao.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.hcode.hoj.mapper.UserAcproblemMapper;
import top.hcode.hoj.pojo.entity.user.UserAcproblem;
import top.hcode.hoj.dao.UserAcproblemEntityService;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Himit_ZH
 * @since 2020-10-23
 */
@Service
public class UserAcproblemEntityServiceImpl extends ServiceImpl<UserAcproblemMapper, UserAcproblem> implements UserAcproblemEntityService {

    @Override
    public boolean saveIfAbsent(String uid, Long pid, Long submitId) {
        return baseMapper.insertIfAbsent(uid, pid, submitId) > 0;
    }
}
