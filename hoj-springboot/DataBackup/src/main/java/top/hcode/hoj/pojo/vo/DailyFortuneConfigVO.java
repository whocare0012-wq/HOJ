package top.hcode.hoj.pojo.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DailyFortuneConfigVO {

    private Long id;

    private String code;

    private String name;

    private String color;

    private String description;

    private Integer sortOrder;

    private Boolean enabled;

    private Integer legacyScore;

    private Integer recommendedCount;

    private Integer avoidCount;
}
