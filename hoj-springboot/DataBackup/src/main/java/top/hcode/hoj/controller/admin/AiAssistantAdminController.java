package top.hcode.hoj.controller.admin;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.service.oj.AiAssistantService;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai-assistant")
@RequiresAuthentication
@RequiresRoles("root")
public class AiAssistantAdminController {

    @Resource
    private AiAssistantService aiAssistantService;

    @GetMapping("/overview")
    public CommonResult<Map<String, Object>> getOverview(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) String status) {
        return aiAssistantService.getAdminOverview(currentPage, limit, status);
    }

    @PutMapping("/config")
    public CommonResult<Map<String, Object>> updateConfig(
            @RequestBody Map<String, Object> config) {
        return aiAssistantService.updateConfig(config);
    }

    @PostMapping("/api-keys")
    public CommonResult<Map<String, Object>> createApiKey(
            @RequestBody Map<String, Object> apiKey) {
        return aiAssistantService.createApiKey(apiKey);
    }

    @PutMapping("/api-keys/{id}")
    public CommonResult<Map<String, Object>> updateApiKey(
            @PathVariable Long id,
            @RequestBody Map<String, Object> apiKey) {
        return aiAssistantService.updateApiKey(id, apiKey);
    }

    @DeleteMapping("/api-keys/{id}")
    public CommonResult<Void> deleteApiKey(@PathVariable Long id) {
        return aiAssistantService.deleteApiKey(id);
    }
}
