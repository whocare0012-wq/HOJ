package top.hcode.hoj.service.oj;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class OjPointsValidationTest {
    @Test void acceptsBoundariesAndTrailingZeros() {
        for (String value : new String[]{"0", "0.01", "20.00", "2.5000", "100000"})
            assertDoesNotThrow(() -> OjPointsService.validatePoints(new BigDecimal(value)));
    }
    @Test void rejectsMissingNegativeExcessPrecisionAndOversize() {
        assertThrows(IllegalArgumentException.class, () -> OjPointsService.validatePoints(null));
        for (String value : new String[]{"-0.01", "0.001", "100000.01", "1000000000000000000"})
            assertThrows(IllegalArgumentException.class, () -> OjPointsService.validatePoints(new BigDecimal(value)));
    }
}
