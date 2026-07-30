package top.hcode.hoj.pojo.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class DailyFortuneAdvicePoolVO {

    private List<DailyFortuneAdviceItemVO> recommended = new ArrayList<>();

    private List<DailyFortuneAdviceItemVO> avoid = new ArrayList<>();
}
