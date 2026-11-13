SELECT ranked.* FROM (
          SELECT ui.uuid AS uid,ui.nickname,ui.username,LEFT(ui.signature,100) AS signature,
            ui.avatar,ui.title_name,ui.title_color,COALESCE(ss.score,0) AS score,
            COALESCE(ss.ac,0) AS ac,
            (SELECT COUNT(*) FROM judge WHERE uid=ui.uuid AND cid=0 AND gid IS NULL) AS total,
            RANK() OVER (ORDER BY COALESCE(ss.score,0) DESC,COALESCE(ss.ac,0) DESC) AS rank_position
          FROM user_info ui LEFT JOIN (
            SELECT uid,SUM(points) AS score,SUM(accepted) AS ac
            FROM oj_problem_points WHERE gid IS NULL GROUP BY uid
          ) ss ON ss.uid=ui.uuid WHERE ui.status=0
        ) ranked ORDER BY rank_position ASC,uid ASC;