package top.hcode.hoj.controller.admin;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.vo.DailyFortuneAdminOverviewVO;
import top.hcode.hoj.pojo.vo.DailyFortuneAdviceItemVO;
import top.hcode.hoj.pojo.vo.DailyFortuneAdvicePoolVO;
import top.hcode.hoj.pojo.vo.DailyFortuneConfigVO;
import top.hcode.hoj.service.oj.DailyCheckInService;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/daily-check-in")
@RequiresRoles("root")
public class DailyCheckInAdminController {

    @Resource
    private DailyCheckInService dailyCheckInService;

    @GetMapping("/advice-pools")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<Map<String, DailyFortuneAdvicePoolVO>> getAdvicePools() {
        return dailyCheckInService.getAdvicePools();
    }

    @PutMapping("/advice-pools/{fortuneType}")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<DailyFortuneAdvicePoolVO> updateAdvicePool(
            @PathVariable String fortuneType,
            @RequestBody DailyFortuneAdvicePoolVO advicePool) {
        return dailyCheckInService.updateAdvicePool(fortuneType, advicePool);
    }

    @GetMapping("/overview")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<DailyFortuneAdminOverviewVO> getAdminOverview() {
        return dailyCheckInService.getAdminOverview();
    }

    @PostMapping("/fortunes")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<DailyFortuneConfigVO> createFortune(
            @RequestBody DailyFortuneConfigVO fortune) {
        return dailyCheckInService.createFortune(fortune);
    }

    @PutMapping("/fortunes/{id}")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<DailyFortuneConfigVO> updateFortune(
            @PathVariable Long id,
            @RequestBody DailyFortuneConfigVO fortune) {
        return dailyCheckInService.updateFortune(id, fortune);
    }

    @DeleteMapping("/fortunes/{id}")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<Void> deleteFortune(@PathVariable Long id) {
        return dailyCheckInService.deleteFortune(id);
    }

    @GetMapping("/fortunes/{fortuneType}/advice")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<DailyFortuneAdvicePoolVO> getFortuneAdvice(
            @PathVariable String fortuneType) {
        return dailyCheckInService.getFortuneAdvice(fortuneType);
    }

    @PostMapping("/fortunes/{fortuneType}/advice/{adviceType}")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<DailyFortuneAdviceItemVO> createAdvice(
            @PathVariable String fortuneType,
            @PathVariable String adviceType,
            @RequestBody DailyFortuneAdviceItemVO item) {
        return dailyCheckInService.createAdvice(fortuneType, adviceType, item);
    }

    @PutMapping("/advice/{id}")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<DailyFortuneAdviceItemVO> updateAdvice(
            @PathVariable Long id,
            @RequestBody DailyFortuneAdviceItemVO item) {
        return dailyCheckInService.updateAdvice(id, item);
    }

    @DeleteMapping("/advice/{id}")
    @RequiresAuthentication
    @RequiresRoles("root")
    public CommonResult<Void> deleteAdvice(@PathVariable Long id) {
        return dailyCheckInService.deleteAdvice(id);
    }
}
