package top.hcode.hoj.service.oj.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.hcode.hoj.pojo.vo.DailyFortuneAdviceItemVO;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyCheckInServiceImplTest {

    @Test
    void selectsOnlyOneAdviceItemForEachAdviceType() {
        DailyCheckInServiceImpl service = new DailyCheckInServiceImpl();
        List<DailyFortuneAdviceItemVO> pool = Arrays.asList(
                new DailyFortuneAdviceItemVO("one", "first"),
                new DailyFortuneAdviceItemVO("two", "second"),
                new DailyFortuneAdviceItemVO("three", "third"));

        List<DailyFortuneAdviceItemVO> selected = ReflectionTestUtils.invokeMethod(
                service,
                "selectAdviceItems",
                pool,
                "test-user",
                LocalDate.of(2026, 7, 30),
                "recommended");

        assertEquals(1, selected.size());
    }
}
