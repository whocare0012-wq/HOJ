package top.hcode.hoj.schedule;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodeforcesClientTest {
    private final CodeforcesClient client = new CodeforcesClient();

    @Test void acceptsRatedAndUnratedUsers() throws Exception {
        assertEquals(2100, client.parseRating("{\"status\":\"OK\",\"result\":[{\"handle\":\"Tourist\",\"rating\":2100}]}", "tourist"));
        assertNull(client.parseRating("{\"status\":\"OK\",\"result\":[{\"handle\":\"newuser\"}]}", "newuser"));
    }

    @Test void rejectsFailedEmptyMismatchedAndMalformedResponses() {
        for (String body : new String[]{"{\"status\":\"FAILED\"}", "<html>unavailable</html>",
                "{\"status\":\"OK\",\"result\":[]}",
                "{\"status\":\"OK\",\"result\":[{\"handle\":\"other\",\"rating\":100}]}",
                "{\"status\":\"OK\",\"result\":[{\"handle\":\"tourist\",\"rating\":\"bad\"}]}"}) {
            assertFalse(assertThrows(CodeforcesClient.FetchFailure.class,
                    () -> client.parseRating(body, "tourist")).isRetryable());
        }
    }
}
