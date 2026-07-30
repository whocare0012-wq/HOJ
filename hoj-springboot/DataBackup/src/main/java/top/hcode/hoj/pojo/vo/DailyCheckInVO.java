package top.hcode.hoj.pojo.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Homepage daily check-in status.
 */
@Data
@Accessors(chain = true)
public class DailyCheckInVO {

    private Boolean checkedIn;

    private Boolean newlyCheckedIn;

    private String checkInDate;

    private Integer totalCheckInDays;

    private Integer consecutiveCheckInDays;

    private String displayName;

    private String fortuneType;

    private String fortuneName;

    private String fortuneColor;

    private String fortuneDescription;

    private List<DailyFortuneAdviceItemVO> recommendedItems;

    private List<DailyFortuneAdviceItemVO> avoidItems;

    /**
     * Legacy fields retained so older frontends remain compatible during a
     * rolling upgrade.
     */
    private Integer adviceIndex;

    private Integer fortuneScore;

    private Integer focusScore;

    private Integer practiceScore;

    private Integer examScore;
}
