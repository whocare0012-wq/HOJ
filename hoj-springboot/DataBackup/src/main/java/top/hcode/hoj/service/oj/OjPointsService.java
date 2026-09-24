package top.hcode.hoj.service.oj;

import org.apache.shiro.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.hcode.hoj.pojo.dto.ProblemDifficultyConfigDTO;
import top.hcode.hoj.shiro.AccountProfile;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

/** Read current values on demand so difficulty edits are immediately reflected everywhere. */
@Service
public class OjPointsService {
    @Resource private JdbcTemplate jdbcTemplate;

    public Map<String,Object> preview(List<ProblemDifficultyConfigDTO> requested) {
        if (requested == null || requested.isEmpty() || requested.size()>50)
            throw new IllegalArgumentException("需要提供 1 至 50 个难度级别");
        List<Map<String,Object>> changes = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (ProblemDifficultyConfigDTO d : requested) {
            if (d == null) throw new IllegalArgumentException("难度配置不能为空");
            validatePoints(d.getBasePoints());
            if (d.getDifficultyValue()==null) continue;
            List<Map<String,Object>> rows=jdbcTemplate.queryForList(
                    "SELECT display_text,base_points FROM problem_difficulty_config WHERE difficulty_value=?",d.getDifficultyValue());
            if (rows.isEmpty()) throw new IllegalArgumentException("难度编号不存在");
            BigDecimal before=(BigDecimal)rows.get(0).get("base_points");
            if (before.compareTo(d.getBasePoints())==0) continue;
            Map<String,Object> change=new LinkedHashMap<>();
            change.put("name",rows.get(0).get("display_text"));
            change.put("oldPoints",before); change.put("newPoints",d.getBasePoints());
            changes.add(change); ids.add(d.getDifficultyValue());
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("changes",changes);
        result.put("problemCount",0);result.put("userCount",0);
        if (!ids.isEmpty()) {
            String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
            result.put("problemCount",jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM problem WHERE difficulty IN ("+placeholders+")",Long.class,ids.toArray()));
            result.put("userCount",jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT s.uid) FROM oj_problem_points s JOIN problem p ON p.id=s.pid " +
                    "WHERE s.completion_ratio>0 AND p.difficulty IN ("+placeholders+")",Long.class,ids.toArray()));
        }
        return result;
    }

    public static void validatePoints(BigDecimal value) {
        if (value==null || value.signum()<0 || value.compareTo(new BigDecimal("100000"))>0
                || value.stripTrailingZeros().scale()>2)
            throw new IllegalArgumentException("基础积分必须为 0 至 100000，最多保留两位小数");
    }

    public void logDifficulty(Integer id,String name,BigDecimal before,BigDecimal after) {
        if (before!=null && before.compareTo(after)==0) return;
        long problems=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM problem WHERE difficulty=?",Long.class,id);
        long users=jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT s.uid) FROM oj_problem_points s JOIN problem p ON p.id=s.pid WHERE p.difficulty=? AND s.completion_ratio>0",Long.class,id);
        log("difficulty",id.longValue(),name,before,after,problems,users);
    }

    public void logProblem(Long pid,String name,Integer before,Integer after) {
        if (Objects.equals(before,after)) return;
        long users=jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT uid) FROM oj_problem_points WHERE pid=? AND completion_ratio>0",Long.class,pid);
        log("problem",pid,name,before==null?null:new BigDecimal(before),after==null?null:new BigDecimal(after),1,users);
    }

    private void log(String type,Long id,String name,BigDecimal before,BigDecimal after,long problems,long users) {
        AccountProfile operator=(AccountProfile)SecurityUtils.getSubject().getPrincipal();
        jdbcTemplate.update("INSERT INTO oj_points_change_log (operator_uid,target_type,target_id,target_name,old_value,new_value,affected_problems,affected_users,gmt_create) VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP())",
                operator==null?null:operator.getUid(),type,id,name,before,after,problems,users);
    }

    public List<Map<String,Object>> history() {
        return jdbcTemplate.queryForList("SELECT l.id,l.target_type,l.target_name,l.old_value,l.new_value,l.affected_problems,l.affected_users,DATE_FORMAT(l.gmt_create + INTERVAL 8 HOUR,'%Y-%m-%d %H:%i:%s') AS gmt_create,COALESCE(u.username,'系统') AS operator_name FROM oj_points_change_log l LEFT JOIN user_info u ON u.uuid=l.operator_uid ORDER BY l.id DESC LIMIT 50");
    }
}
