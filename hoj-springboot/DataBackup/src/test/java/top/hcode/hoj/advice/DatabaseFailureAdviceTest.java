package top.hcode.hoj.advice;

import org.apache.catalina.connector.ClientAbortException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseFailureAdviceTest {
    GlobalExceptionAdvice advice = new GlobalExceptionAdvice();
    @Test void describesCharsetFailureWithoutExposingSql() {
        String msg = advice.handleBatchUpdateException(new PersistenceException(
                new SQLException("private_table private_value", "HY000", 1366))).getMsg();
        assertTrue(msg.contains("字符"));
        assertTrue(msg.contains("错误编号"));
        assertFalse(msg.contains("重复"));
        assertFalse(msg.contains("private_"));
    }
    @Test void distinguishesDuplicatesAndForeignKeys() {
        assertTrue(advice.handleSQLException(new SQLException("secret", "23000", 1062)).getMsg().contains("重复"));
        assertTrue(advice.handleSQLException(new SQLException("secret", "23000", 1451)).getMsg().contains("关联"));
        assertFalse(advice.handleSQLException(new SQLException("secret", "HY000", 9999)).getMsg().contains("secret"));
    }
    @Test void clientAbortDoesNotGenerateAnotherResponse() {
        assertDoesNotThrow(() -> advice.handleClientAbort(new ClientAbortException("Broken pipe")));
    }
}
