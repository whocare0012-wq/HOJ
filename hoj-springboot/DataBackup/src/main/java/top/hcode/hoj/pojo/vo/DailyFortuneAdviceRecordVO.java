package top.hcode.hoj.pojo.vo;

import lombok.Data;

/**
 * Internal row projection for the configurable fortune advice table.
 */
@Data
public class DailyFortuneAdviceRecordVO {

    private Long id;

    private String fortuneType;

    private String adviceType;

    private String title;

    private String description;

    private Integer sortOrder;

    private Boolean enabled;

    private Long usageCount;
}
