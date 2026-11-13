package top.hcode.hoj.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounds audit queries and keeps the selected page valid when its record count changes. */
public final class AgentAuditPage {
    private final long total;
    private final int currentPage;
    private final int pageSize;

    public AgentAuditPage(int requestedPage, int requestedSize, long total) {
        this.total = Math.max(0, total);
        this.pageSize = Math.max(1, Math.min(200, requestedSize));
        long lastPage = this.total == 0 ? 1 : (this.total - 1) / pageSize + 1;
        this.currentPage = (int) Math.min(Math.max(1, requestedPage), lastPage);
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getOffset() {
        return (long) (currentPage - 1) * pageSize;
    }

    public Map<String, Object> result(List<Map<String, Object>> records) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("currentPage", currentPage);
        result.put("pageSize", pageSize);
        return result;
    }
}
