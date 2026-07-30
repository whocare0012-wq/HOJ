package top.hcode.hoj.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;
import top.hcode.hoj.pojo.vo.DailyFortuneConfigVO;

import java.util.List;

@Mapper
@Repository
public interface DailyFortuneConfigMapper {

    @Select("SELECT id, code, name, color, description, sort_order AS sortOrder, " +
            "enabled, legacy_score AS legacyScore " +
            "FROM daily_fortune_config ORDER BY sort_order ASC, id ASC")
    List<DailyFortuneConfigVO> selectAll();

    @Select("SELECT id, code, name, color, description, sort_order AS sortOrder, " +
            "enabled, legacy_score AS legacyScore " +
            "FROM daily_fortune_config WHERE enabled = 1 " +
            "ORDER BY sort_order ASC, id ASC")
    List<DailyFortuneConfigVO> selectEnabled();

    @Select("SELECT id, code, name, color, description, sort_order AS sortOrder, " +
            "enabled, legacy_score AS legacyScore " +
            "FROM daily_fortune_config WHERE id = #{id}")
    DailyFortuneConfigVO selectById(@Param("id") Long id);

    @Select("SELECT id, code, name, color, description, sort_order AS sortOrder, " +
            "enabled, legacy_score AS legacyScore " +
            "FROM daily_fortune_config WHERE code = #{code}")
    DailyFortuneConfigVO selectByCode(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM daily_fortune_config")
    int countAll();

    @Select("SELECT COUNT(*) FROM daily_fortune_config WHERE enabled = 1")
    int countEnabled();

    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM daily_fortune_config")
    int selectMaxSortOrder();

    @Insert("INSERT INTO daily_fortune_config " +
            "(code, name, color, description, sort_order, enabled, legacy_score, " +
            "gmt_create, gmt_modified) VALUES " +
            "(#{code}, #{name}, #{color}, #{description}, #{sortOrder}, #{enabled}, " +
            "#{legacyScore}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DailyFortuneConfigVO fortune);

    @Update("UPDATE daily_fortune_config SET name = #{name}, color = #{color}, " +
            "description = #{description}, sort_order = #{sortOrder}, " +
            "enabled = #{enabled}, legacy_score = #{legacyScore}, " +
            "gmt_modified = NOW() WHERE id = #{id}")
    int update(DailyFortuneConfigVO fortune);

    @Delete("DELETE FROM daily_fortune_config WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
