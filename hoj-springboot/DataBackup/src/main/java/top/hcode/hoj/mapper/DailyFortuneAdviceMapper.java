package top.hcode.hoj.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;
import top.hcode.hoj.pojo.vo.DailyFortuneAdviceItemVO;
import top.hcode.hoj.pojo.vo.DailyFortuneAdviceRecordVO;

import java.util.List;

@Mapper
@Repository
public interface DailyFortuneAdviceMapper {

    @Select("SELECT id, fortune_type AS fortuneType, advice_type AS adviceType, " +
            "title, description, sort_order AS sortOrder, enabled, " +
            "usage_count AS usageCount " +
            "FROM daily_fortune_advice " +
            "WHERE fortune_type = #{fortuneType} " +
            "ORDER BY advice_type ASC, sort_order ASC, id ASC")
    List<DailyFortuneAdviceRecordVO> selectByFortuneType(@Param("fortuneType") String fortuneType);

    @Select("SELECT id, fortune_type AS fortuneType, advice_type AS adviceType, " +
            "title, description, sort_order AS sortOrder, enabled, " +
            "usage_count AS usageCount " +
            "FROM daily_fortune_advice " +
            "WHERE fortune_type = #{fortuneType} AND enabled = 1 " +
            "ORDER BY advice_type ASC, sort_order ASC, id ASC")
    List<DailyFortuneAdviceRecordVO> selectEnabledByFortuneType(
            @Param("fortuneType") String fortuneType);

    @Select("SELECT id, fortune_type AS fortuneType, advice_type AS adviceType, " +
            "title, description, sort_order AS sortOrder, enabled, " +
            "usage_count AS usageCount FROM daily_fortune_advice WHERE id = #{id}")
    DailyFortuneAdviceRecordVO selectById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM daily_fortune_advice " +
            "WHERE fortune_type = #{fortuneType} AND advice_type = #{adviceType}")
    int countByFortuneTypeAndAdviceType(
            @Param("fortuneType") String fortuneType,
            @Param("adviceType") String adviceType);

    @Select("SELECT COUNT(*) FROM daily_fortune_advice " +
            "WHERE fortune_type = #{fortuneType} AND advice_type = #{adviceType} " +
            "AND enabled = 1")
    int countEnabledByFortuneTypeAndAdviceType(
            @Param("fortuneType") String fortuneType,
            @Param("adviceType") String adviceType);

    @Select("SELECT COUNT(*) FROM daily_fortune_advice WHERE advice_type = #{adviceType}")
    int countByAdviceType(@Param("adviceType") String adviceType);

    @Select("<script>SELECT COUNT(*) FROM daily_fortune_advice " +
            "WHERE fortune_type = #{fortuneType} AND advice_type = #{adviceType} " +
            "AND LOWER(title) = LOWER(#{title}) " +
            "<if test='excludeId != null'>AND id &lt;&gt; #{excludeId}</if></script>")
    int countDuplicateTitle(
            @Param("fortuneType") String fortuneType,
            @Param("adviceType") String adviceType,
            @Param("title") String title,
            @Param("excludeId") Long excludeId);

    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM daily_fortune_advice " +
            "WHERE fortune_type = #{fortuneType} AND advice_type = #{adviceType}")
    int selectMaxSortOrder(
            @Param("fortuneType") String fortuneType,
            @Param("adviceType") String adviceType);

    @Delete("DELETE FROM daily_fortune_advice WHERE fortune_type = #{fortuneType}")
    int deleteByFortuneType(@Param("fortuneType") String fortuneType);

    @Delete("DELETE FROM daily_fortune_advice WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Insert("INSERT INTO daily_fortune_advice " +
            "(fortune_type, advice_type, title, description, sort_order, enabled, " +
            "usage_count, gmt_create, gmt_modified) VALUES " +
            "(#{fortuneType}, #{adviceType}, #{title}, #{description}, #{sortOrder}, " +
            "#{enabled}, #{usageCount}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DailyFortuneAdviceItemVO item);

    @Update("UPDATE daily_fortune_advice SET title = #{title}, " +
            "description = #{description}, sort_order = #{sortOrder}, " +
            "enabled = #{enabled}, gmt_modified = NOW() WHERE id = #{id}")
    int update(DailyFortuneAdviceItemVO item);

    @Update("<script>UPDATE daily_fortune_advice " +
            "SET usage_count = usage_count + 1, gmt_modified = NOW() " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    int incrementUsage(@Param("ids") List<Long> ids);
}
