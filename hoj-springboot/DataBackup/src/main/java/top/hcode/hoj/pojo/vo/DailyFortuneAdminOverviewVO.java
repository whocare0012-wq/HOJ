package top.hcode.hoj.pojo.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class DailyFortuneAdminOverviewVO {

    private Integer fortuneCount;

    private Integer recommendedCount;

    private Integer avoidCount;

    private Integer todayCheckInCount;

    private List<DailyFortuneConfigVO> fortunes = new ArrayList<>();
}
