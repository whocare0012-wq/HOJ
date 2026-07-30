package top.hcode.hoj.service.oj;

import top.hcode.hoj.common.result.CommonResult;
import top.hcode.hoj.pojo.dto.ProblemDifficultyConfigDTO;
import top.hcode.hoj.pojo.vo.ProblemDifficultyVO;

import java.util.List;

public interface ProblemDifficultyService {

    CommonResult<List<ProblemDifficultyVO>> getProblemDifficulties();

    CommonResult<List<ProblemDifficultyVO>> updateProblemDifficulties(
            List<ProblemDifficultyConfigDTO> difficulties);
}
