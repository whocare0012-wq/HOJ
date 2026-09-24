package top.hcode.hoj.controller.admin;

import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.dto.ProblemDifficultyConfigDTO;
import top.hcode.hoj.pojo.vo.ProblemDifficultyVO;
import top.hcode.hoj.service.oj.ProblemDifficultyService;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/admin/problem-difficulties")
public class ProblemDifficultyAdminController {

    @Resource
    private ProblemDifficultyService problemDifficultyService;

    @Resource private top.hcode.hoj.service.oj.OjPointsService ojPointsService;

    @org.springframework.web.bind.annotation.PostMapping("/preview")
    @RequiresAuthentication
    @RequiresRoles(value = {"root", "problem_admin"}, logical = Logical.OR)
    public CommonResult<java.util.Map<String,Object>> preview(@RequestBody List<ProblemDifficultyConfigDTO> difficulties) {
        try { return CommonResult.successResponse(ojPointsService.preview(difficulties)); }
        catch (IllegalArgumentException e) { return CommonResult.errorResponse(e.getMessage()); }
    }

    @GetMapping("/history")
    @RequiresAuthentication
    @RequiresRoles(value = {"root", "problem_admin"}, logical = Logical.OR)
    public CommonResult<java.util.List<java.util.Map<String,Object>>> history() {
        return CommonResult.successResponse(ojPointsService.history());
    }

    @GetMapping
    @RequiresAuthentication
    @RequiresRoles(value = {"root", "problem_admin"}, logical = Logical.OR)
    public CommonResult<List<ProblemDifficultyVO>> getProblemDifficulties() {
        return problemDifficultyService.getProblemDifficulties();
    }

    @PutMapping
    @RequiresAuthentication
    @RequiresRoles(value = {"root", "problem_admin"}, logical = Logical.OR)
    public CommonResult<List<ProblemDifficultyVO>> updateProblemDifficulties(
            @RequestBody List<ProblemDifficultyConfigDTO> difficulties) {
        return problemDifficultyService.updateProblemDifficulties(difficulties);
    }
}
