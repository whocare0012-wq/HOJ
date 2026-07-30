package top.hcode.hoj.service.oj;

import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.dto.AiAssistantRequestDTO;

import java.util.Map;

public interface AiAssistantService {

    CommonResult<Map<String, Object>> getUserStatus(
            Long problemId,
            String sourceType,
            Long trainingId);

    CommonResult<Map<String, Object>> submitRequest(AiAssistantRequestDTO request);

    CommonResult<Map<String, Object>> getUserRequest(Long requestId);

    CommonResult<Map<String, Object>> getAdminOverview(
            Integer currentPage,
            Integer limit,
            String status);

    CommonResult<Map<String, Object>> updateConfig(Map<String, Object> config);

    CommonResult<Map<String, Object>> createApiKey(Map<String, Object> apiKey);

    CommonResult<Map<String, Object>> updateApiKey(Long id, Map<String, Object> apiKey);

    CommonResult<Void> deleteApiKey(Long id);
}
