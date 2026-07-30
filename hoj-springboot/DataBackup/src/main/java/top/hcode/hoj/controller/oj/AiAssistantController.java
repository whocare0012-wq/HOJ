package top.hcode.hoj.controller.oj;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.dto.AiAssistantRequestDTO;
import top.hcode.hoj.service.oj.AiAssistantService;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-assistant")
@RequiresAuthentication
public class AiAssistantController {

    @Resource
    private AiAssistantService aiAssistantService;

    @GetMapping("/status")
    public CommonResult<Map<String, Object>> getStatus(
            @RequestParam Long problemId,
            @RequestParam String sourceType,
            @RequestParam(required = false) Long trainingId) {
        return aiAssistantService.getUserStatus(problemId, sourceType, trainingId);
    }

    @PostMapping("/requests")
    public CommonResult<Map<String, Object>> submitRequest(
            @RequestBody AiAssistantRequestDTO request) {
        return aiAssistantService.submitRequest(request);
    }

    @GetMapping("/requests/{requestId}")
    public CommonResult<Map<String, Object>> getRequest(
            @PathVariable Long requestId) {
        return aiAssistantService.getUserRequest(requestId);
    }
}
