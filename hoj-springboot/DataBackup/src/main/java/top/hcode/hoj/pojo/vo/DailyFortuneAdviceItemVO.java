package top.hcode.hoj.pojo.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DailyFortuneAdviceItemVO {

    private Long id;

    private String fortuneType;

    private String adviceType;

    private String title;

    private String description;

    private Integer sortOrder;

    private Boolean enabled;

    private Long usageCount;

    public DailyFortuneAdviceItemVO(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
