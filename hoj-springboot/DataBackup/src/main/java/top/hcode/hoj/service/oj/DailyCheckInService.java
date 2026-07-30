package top.hcode.hoj.service.oj;

import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.vo.DailyFortuneAdminOverviewVO;
import top.hcode.hoj.pojo.vo.DailyFortuneAdviceItemVO;
import top.hcode.hoj.pojo.vo.DailyFortuneAdvicePoolVO;
import top.hcode.hoj.pojo.vo.DailyCheckInVO;
import top.hcode.hoj.pojo.vo.DailyFortuneConfigVO;

import java.util.Map;

public interface DailyCheckInService {

    CommonResult<DailyCheckInVO> getStatus();

    CommonResult<DailyCheckInVO> checkIn();

    CommonResult<Map<String, DailyFortuneAdvicePoolVO>> getAdvicePools();

    CommonResult<DailyFortuneAdvicePoolVO> updateAdvicePool(
            String fortuneType,
            DailyFortuneAdvicePoolVO advicePool);

    CommonResult<DailyFortuneAdminOverviewVO> getAdminOverview();

    CommonResult<DailyFortuneConfigVO> createFortune(DailyFortuneConfigVO fortune);

    CommonResult<DailyFortuneConfigVO> updateFortune(
            Long id,
            DailyFortuneConfigVO fortune);

    CommonResult<Void> deleteFortune(Long id);

    CommonResult<DailyFortuneAdvicePoolVO> getFortuneAdvice(String fortuneType);

    CommonResult<DailyFortuneAdviceItemVO> createAdvice(
            String fortuneType,
            String adviceType,
            DailyFortuneAdviceItemVO item);

    CommonResult<DailyFortuneAdviceItemVO> updateAdvice(
            Long id,
            DailyFortuneAdviceItemVO item);

    CommonResult<Void> deleteAdvice(Long id);
}
