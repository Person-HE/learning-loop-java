package com.closeloop.infrastructure.persistence.mapper;

import com.closeloop.infrastructure.persistence.entity.KpEntity;
import com.closeloop.infrastructure.persistence.entity.KpHistoryEntity;
import com.closeloop.infrastructure.persistence.entity.KpStateEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KpMapper {

    @Select("SELECT COUNT(*) FROM kp")
    int countAll();

    @Select("SELECT * FROM kp ORDER BY id")
    List<KpEntity> selectAll();

    @Select("SELECT * FROM kp_state")
    List<KpStateEntity> selectAllStates();

    @Select("SELECT * FROM kp_history WHERE kp_id = #{kpId} ORDER BY review_date, id")
    List<KpHistoryEntity> selectHistory(String kpId);

    @Insert("""
            INSERT INTO kp(id,domain,chapter,title,difficulty,hot,category,path,kb_status,is_lc,meta_json)
            VALUES (#{id},#{domain},#{chapter},#{title},#{difficulty},#{hot},#{category},#{path},#{kbStatus},#{isLc},#{metaJson})
            ON DUPLICATE KEY UPDATE domain=VALUES(domain), chapter=VALUES(chapter), title=VALUES(title),
              difficulty=VALUES(difficulty), hot=VALUES(hot), category=VALUES(category), path=VALUES(path),
              kb_status=VALUES(kb_status), is_lc=VALUES(is_lc), meta_json=VALUES(meta_json)
            """)
    int upsert(KpEntity e);

    @Insert("""
            INSERT INTO kp_state(kp_id,status,ease,interval_days,due_date,stability,lapses,last_score,reviews,last_review_date,plan_json,version)
            VALUES (#{kpId},#{status},#{ease},#{intervalDays},#{dueDate},#{stability},#{lapses},#{lastScore},#{reviews},#{lastReviewDate},#{planJson},0)
            ON DUPLICATE KEY UPDATE status=VALUES(status), ease=VALUES(ease), interval_days=VALUES(interval_days),
              due_date=VALUES(due_date), stability=VALUES(stability), lapses=VALUES(lapses), last_score=VALUES(last_score),
              reviews=VALUES(reviews), last_review_date=VALUES(last_review_date), plan_json=VALUES(plan_json), version=version+1
            """)
    int upsertState(KpStateEntity e);

    @Insert("INSERT INTO kp_history(kp_id,review_date,score,q,stability) VALUES (#{kpId},#{reviewDate},#{score},#{q},#{stability})")
    int insertHistory(KpHistoryEntity e);

    @Delete("DELETE FROM kp_history WHERE kp_id = #{kpId}")
    int deleteHistory(String kpId);

    @Select("SELECT kp_id FROM kp_state WHERE due_date IS NOT NULL AND due_date <= #{today} AND status <> 'new'")
    List<String> selectDueKpIds(String today);
}
