package com.closeloop.infrastructure.persistence.mapper;

import com.closeloop.infrastructure.persistence.entity.AnswerRecordEntity;
import com.closeloop.infrastructure.persistence.entity.GapEntity;
import com.closeloop.infrastructure.persistence.entity.QuestionHistEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface LearningMapper {

    // ---------- answer_record ----------
    @Insert("""
            INSERT INTO answer_record(id,kp_id,question_id,question,answer_text,code,think,spoken,score,level,verdict,profile,diagnose_json)
            VALUES (#{id},#{kpId},#{questionId},#{question},#{answerText},#{code},#{think},#{spoken},#{score},#{level},#{verdict},#{profile},#{diagnoseJson})
            ON DUPLICATE KEY UPDATE score=VALUES(score), level=VALUES(level), verdict=VALUES(verdict), diagnose_json=VALUES(diagnose_json)
            """)
    int insertRecord(AnswerRecordEntity e);

    @Select("SELECT * FROM answer_record ORDER BY created_at, id")
    List<AnswerRecordEntity> selectAllRecords();

    @Select("""
            SELECT DATE(created_at) AS d, COUNT(*) AS cnt, AVG(score) AS avgScore
            FROM answer_record
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)
            GROUP BY DATE(created_at)
            ORDER BY d
            """)
    List<Map<String, Object>> selectTrend14();

    // ---------- gap ----------
    @Insert("""
            INSERT INTO gap(id,kp_id,label,description,created_at,resolved_at)
            VALUES (#{id},#{kpId},#{label},#{description},#{createdAt},#{resolvedAt})
            ON DUPLICATE KEY UPDATE label=VALUES(label), description=VALUES(description), resolved_at=VALUES(resolved_at)
            """)
    int upsertGap(GapEntity e);

    @Select("SELECT * FROM gap ORDER BY created_at, id")
    List<GapEntity> selectAllGaps();

    // ---------- question_hist ----------
    @Insert("""
            INSERT INTO question_hist(kp_id,question_id,question,answer_points,point,score,tries,due_date,last_at)
            VALUES (#{kpId},#{questionId},#{question},#{answerPointsJson},#{point},#{score},#{tries},#{dueDate},#{lastAt})
            ON DUPLICATE KEY UPDATE question=VALUES(question), answer_points=VALUES(answer_points),
              point=VALUES(point), score=VALUES(score), tries=VALUES(tries), due_date=VALUES(due_date), last_at=VALUES(last_at)
            """)
    int upsertQuestionHist(QuestionHistEntity e);

    // 列 answer_points 与属性 answerPointsJson 不同名，需别名才能被 map-underscore 绑定
    @Select("SELECT *, answer_points AS answer_points_json FROM question_hist WHERE kp_id = #{kpId}")
    List<QuestionHistEntity> selectQuestionHists(String kpId);

    @Delete("DELETE FROM question_hist WHERE kp_id = #{kpId}")
    int deleteQuestionHists(String kpId);
}
