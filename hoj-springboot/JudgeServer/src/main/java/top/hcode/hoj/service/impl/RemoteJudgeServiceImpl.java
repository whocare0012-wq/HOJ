package top.hcode.hoj.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.hcode.hoj.pojo.entity.judge.JudgeServer;
import top.hcode.hoj.pojo.entity.judge.RemoteJudgeAccount;
import top.hcode.hoj.dao.JudgeServerEntityService;
import top.hcode.hoj.dao.RemoteJudgeAccountEntityService;
import top.hcode.hoj.service.RemoteJudgeService;

/**
 * @Author: Himit_ZH
 * @Date: 2021/12/7 23:57
 * @Description:
 */
@Service
@Slf4j(topic = "hoj")
public class RemoteJudgeServiceImpl implements RemoteJudgeService {

    @Autowired
    private RemoteJudgeAccountEntityService remoteJudgeAccountEntityService;

    @Autowired
    private JudgeServerEntityService judgeServerEntityService;

    @Override
    public void changeAccountStatus(String remoteJudge, String username) {

        UpdateWrapper<RemoteJudgeAccount> remoteJudgeAccountUpdateWrapper = new UpdateWrapper<>();
        remoteJudgeAccountUpdateWrapper.set("status", true)
                .eq("username", username);
        if (remoteJudge.equals("GYM")) {
            remoteJudge = "CF";
        }
        remoteJudgeAccountUpdateWrapper.eq("oj", remoteJudge);

        boolean isOk = remoteJudgeAccountEntityService.update(remoteJudgeAccountUpdateWrapper);

        // 更新条件不匹配时重试同一语句结果不会改变，直接记录
        if (!isOk) {
            log.error("Remote Judge：Change Account status to `true` Failed ----------->{}", "oj:" + remoteJudge + ",username:" + username);
        }
    }

    @Override
    public void changeServerSubmitCFStatus(String ip, Integer port) {

        if (StringUtils.isEmpty(ip) || port == null) {
            return;
        }
        UpdateWrapper<JudgeServer> judgeServerUpdateWrapper = new UpdateWrapper<>();
        judgeServerUpdateWrapper.set("cf_submittable", true)
                .eq("ip", ip)
                .eq("is_remote", true)
                .eq("port", port);
        boolean isOk = judgeServerEntityService.update(judgeServerUpdateWrapper);

        if (!isOk) {
            log.error("Remote Judge：Change CF Judge Server Status to `true` Failed! =======>{}", "ip:" + ip + ",port:" + port);
        }
    }
}