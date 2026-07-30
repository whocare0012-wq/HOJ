package top.hcode.hoj.controller.oj;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.vo.DailyCheckInVO;
import top.hcode.hoj.service.oj.DailyCheckInService;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/daily-check-in")
public class DailyCheckInController {

    @Resource
    private DailyCheckInService dailyCheckInService;

    @GetMapping
    @RequiresAuthentication
    public CommonResult<DailyCheckInVO> getStatus() {
        return dailyCheckInService.getStatus();
    }

    @PostMapping
    @RequiresAuthentication
    public CommonResult<DailyCheckInVO> checkIn() {
        return dailyCheckInService.checkIn();
    }
}
