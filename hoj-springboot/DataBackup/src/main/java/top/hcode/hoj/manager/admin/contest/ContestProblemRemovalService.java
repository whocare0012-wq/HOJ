package top.hcode.hoj.manager.admin.contest;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.hcode.hoj.common.exception.StatusFailException;
import top.hcode.hoj.dao.contest.ContestEntityService;
import top.hcode.hoj.dao.contest.ContestProblemEntityService;
import top.hcode.hoj.dao.contest.ContestRecordEntityService;
import top.hcode.hoj.dao.judge.JudgeEntityService;
import top.hcode.hoj.pojo.entity.contest.Contest;
import top.hcode.hoj.pojo.entity.contest.ContestProblem;
import top.hcode.hoj.pojo.entity.contest.ContestRecord;
import top.hcode.hoj.pojo.entity.judge.Judge;

/** Shared by the admin and group paths. Never delete historical submissions. */
@Service
public class ContestProblemRemovalService {
    @Autowired private ContestEntityService contests;
    @Autowired private ContestProblemEntityService problems;
    @Autowired private ContestRecordEntityService records;
    @Autowired private JudgeEntityService judges;

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long pid, Long cid) throws StatusFailException {
        if (pid == null || cid == null) {
            throw new StatusFailException("比赛和题目不能为空");
        }
        // Serialize contest changes and FK-linked competition record insertions.
        Contest contest = contests.getOne(new QueryWrapper<Contest>().eq("id", cid).last("FOR UPDATE"));
        if (contest == null) {
            throw new StatusFailException("移除失败，比赛不存在");
        }
        long now = System.currentTimeMillis();
        if (contest.getStartTime() == null || contest.getEndTime() == null
                || !contest.getEndTime().after(contest.getStartTime())
                || (!contest.getStartTime().after(new java.util.Date(now))
                && contest.getEndTime().after(new java.util.Date(now)))) {
            throw new StatusFailException("比赛进行中或时间配置不完整，不能移除题目");
        }
        ContestProblem problem = problems.getOne(new QueryWrapper<ContestProblem>()
                .eq("cid", cid).eq("pid", pid).last("FOR UPDATE"));
        if (problem == null) {
            throw new StatusFailException("移除失败，题目不在该比赛中");
        }
        if (judges.count(new QueryWrapper<Judge>().eq("cid", cid).eq("pid", pid)) > 0
                || records.count(new QueryWrapper<ContestRecord>().eq("cpid", problem.getId())
                .or(query -> query.eq("cid", cid).eq("pid", pid))) > 0) {
            throw new StatusFailException("该题已有提交或比赛记录，不能移除；历史数据将保留");
        }
        if (!problems.removeById(problem.getId())) {
            throw new StatusFailException("移除失败，请刷新后重试");
        }
    }
}
