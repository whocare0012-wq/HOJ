package top.hcode.hoj.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.service.SystemConfigService;
import top.hcode.hoj.security.JudgeAccessVerifier;

import java.util.HashMap;

/**
 * @Author: Himit_ZH
 * @Date: 2020/12/3 20:12
 * @Description:
 */
@RestController
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private JudgeAccessVerifier judgeAccessVerifier;

    @RequestMapping("/get-sys-config")
    public HashMap<String,Object> getSystemConfig(
            @RequestHeader(value = "X-Judge-Token", required = false)
            String accessToken){
        judgeAccessVerifier.requireValidToken(accessToken);
        return systemConfigService.getSystemConfig();
    }
}
