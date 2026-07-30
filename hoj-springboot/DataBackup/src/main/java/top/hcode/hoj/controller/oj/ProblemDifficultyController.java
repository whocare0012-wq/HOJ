package top.hcode.hoj.controller.oj;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.hcode.hoj.annotation.AnonApi;
import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.vo.ProblemDifficultyVO;
import top.hcode.hoj.service.oj.ProblemDifficultyService;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/problem-difficulties")
@AnonApi
public class ProblemDifficultyController {

    @Resource
    private ProblemDifficultyService problemDifficultyService;

    @GetMapping
    public CommonResult<List<ProblemDifficultyVO>> getProblemDifficulties() {
        return problemDifficultyService.getProblemDifficulties();
    }
}
