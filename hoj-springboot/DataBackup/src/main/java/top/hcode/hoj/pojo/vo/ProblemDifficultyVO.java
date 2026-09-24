package top.hcode.hoj.pojo.vo;

import lombok.Data;

@Data
public class ProblemDifficultyVO {

    private Integer difficultyValue;

    private String displayText;

    private String borderColor;

    private java.math.BigDecimal basePoints;

    private Integer sortOrder;

    private Long problemCount;
}
