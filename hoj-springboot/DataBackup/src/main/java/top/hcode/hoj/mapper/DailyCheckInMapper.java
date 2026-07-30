package top.hcode.hoj.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;

@Mapper
@Repository
public interface DailyCheckInMapper {

    @Select("SELECT COUNT(*) FROM user_daily_check_in " +
            "WHERE uid = #{uid} AND check_in_date = #{checkInDate}")
    int countByUidAndDate(@Param("uid") String uid,
                          @Param("checkInDate") Date checkInDate);

    @Select("SELECT COUNT(*) FROM user_daily_check_in WHERE uid = #{uid}")
    int countByUid(@Param("uid") String uid);

    @Select("SELECT COUNT(*) FROM user_daily_check_in WHERE check_in_date = #{checkInDate}")
    int countByDate(@Param("checkInDate") Date checkInDate);

    @Select("SELECT check_in_date FROM user_daily_check_in " +
            "WHERE uid = #{uid} ORDER BY check_in_date DESC")
    List<Date> selectDatesByUid(@Param("uid") String uid);

    @Insert("INSERT IGNORE INTO user_daily_check_in (uid, check_in_date, gmt_create) " +
            "VALUES (#{uid}, #{checkInDate}, NOW())")
    int insertIgnore(@Param("uid") String uid,
                     @Param("checkInDate") Date checkInDate);
}
