package top.hcode.hoj.schedule;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class CodeforcesClient {
    public Integer fetchRating(String handle, int timeoutMillis) throws FetchFailure {
        try {
            String body = Jsoup.connect("https://codeforces.com/api/user.info")
                    .data("handles", handle).ignoreContentType(true)
                    .timeout(timeoutMillis).maxBodySize(1024 * 1024).execute().body();
            return parseRating(body, handle);
        } catch (HttpStatusException e) {
            int status = e.getStatusCode();
            throw new FetchFailure("http_" + status, status == 429 || status >= 500);
        } catch (IOException e) {
            throw new FetchFailure("network_" + e.getClass().getSimpleName(), true);
        }
    }

    Integer parseRating(String body, String handle) throws FetchFailure {
        try {
            JSONObject response = new JSONObject(body);
            if (!"OK".equals(response.getStr("status"))) {
                // Invalid handles and other API-level failures are not retried blindly.
                throw new FetchFailure("api_failed", false);
            }
            JSONArray result = response.getJSONArray("result");
            if (result == null || result.size() != 1) {
                throw new FetchFailure("invalid_response", false);
            }
            JSONObject user = result.getJSONObject(0);
            if (!handle.equalsIgnoreCase(user.getStr("handle"))) {
                throw new FetchFailure("handle_mismatch", false);
            }
            // A valid, unrated user has no rating. Only this successful response clears it.
            Object rating = user.get("rating");
            if (rating == null) return null;
            if (!(rating instanceof Number) || ((Number) rating).longValue() < 0
                    || ((Number) rating).doubleValue() != ((Number) rating).intValue()) {
                throw new FetchFailure("invalid_rating", false);
            }
            return ((Number) rating).intValue();
        } catch (FetchFailure e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FetchFailure("invalid_response", false);
        }
    }

    public static class FetchFailure extends Exception {
        private final boolean retryable;
        public FetchFailure(String reason, boolean retryable) {
            super(reason);
            this.retryable = retryable;
        }
        public boolean isRetryable() { return retryable; }
    }
}
