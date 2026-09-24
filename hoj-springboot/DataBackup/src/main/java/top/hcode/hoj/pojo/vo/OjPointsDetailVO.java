package top.hcode.hoj.pojo.vo;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class OjPointsDetailVO {
    private String problemId;
    private String title;
    private String difficulty;
    private BigDecimal basePoints;
    private BigDecimal completionRatio;
    private BigDecimal points;
    private Boolean estimated;
}
