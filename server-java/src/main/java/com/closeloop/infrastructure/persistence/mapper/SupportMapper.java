package com.closeloop.infrastructure.persistence.mapper;

import com.closeloop.infrastructure.persistence.entity.QueueItemEntity;
import com.closeloop.infrastructure.persistence.entity.RagChunkEntity;
import com.closeloop.infrastructure.persistence.entity.RagDocEntity;
import com.closeloop.infrastructure.persistence.entity.SessionDayEntity;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SupportMapper {

    // ---------- session_day ----------
    @Insert("""
            INSERT INTO session_day(session_date,cnt,avg_score,new_g,res_g,min_score,spoken,mode)
            VALUES (#{sessionDate},#{cnt},#{avgScore},#{newG},#{resG},#{minScore},#{spoken},#{mode})
            ON DUPLICATE KEY UPDATE cnt=VALUES(cnt), avg_score=VALUES(avg_score), new_g=VALUES(new_g),
              res_g=VALUES(res_g), min_score=VALUES(min_score), spoken=VALUES(spoken), mode=VALUES(mode)
            """)
    int upsertSessionDay(SessionDayEntity e);

    @Select("SELECT * FROM session_day ORDER BY session_date")
    List<SessionDayEntity> selectAllSessionDays();

    // ---------- queue_item ----------
    @Insert("""
            INSERT INTO queue_item(id,kp_id,kind,reason,questions,created_on)
            VALUES (#{id},#{kpId},#{kind},#{reason},#{questionsJson},#{createdOn})
            """)
    int insertQueue(QueueItemEntity e);

    // 列名 questions 与实体属性 questionsJson 不同名，必须显式别名，否则 map-underscore 也映射不到
    @Select("""
            SELECT id,kp_id,kind,reason,questions AS questions_json,created_on,consumed_at
            FROM queue_item WHERE consumed_at IS NULL ORDER BY id
            """)
    List<QueueItemEntity> selectOpenQueue();

    @Delete("DELETE FROM queue_item WHERE consumed_at IS NULL")
    int clearOpenQueue();

    @Update("UPDATE queue_item SET consumed_at = NOW() WHERE id = #{id}")
    int consumeQueue(String id);

    // ---------- app_setting ----------
    @Insert("""
            INSERT INTO app_setting(k,v) VALUES (#{k},#{v})
            ON DUPLICATE KEY UPDATE v=VALUES(v)
            """)
    int upsertSetting(@Param("k") String k, @Param("v") String v);

    @Select("SELECT v FROM app_setting WHERE k = #{k}")
    String selectSetting(String k);

    // ---------- rag ----------
    @Insert("""
            INSERT INTO rag_doc(doc_id,kp_id,domain,title,content_hash,chunk_total,embedded_at)
            VALUES (#{docId},#{kpId},#{domain},#{title},#{contentHash},#{chunkTotal},#{embeddedAt})
            ON DUPLICATE KEY UPDATE kp_id=VALUES(kp_id), domain=VALUES(domain), title=VALUES(title),
              content_hash=VALUES(content_hash), chunk_total=VALUES(chunk_total), embedded_at=VALUES(embedded_at)
            """)
    int upsertRagDoc(RagDocEntity e);

    @Select("SELECT * FROM rag_doc WHERE doc_id = #{docId}")
    RagDocEntity selectRagDoc(String docId);

    @Delete("DELETE FROM rag_chunk WHERE doc_id = #{docId}")
    int deleteChunksByDoc(String docId);

    @Insert("""
            INSERT INTO rag_chunk(chunk_id,doc_id,kp_id,chunk_index,heading,text,token_est,embedding,embed_model)
            VALUES (#{chunkId},#{docId},#{kpId},#{chunkIndex},#{heading},#{text},#{tokenEst},#{embedding},#{embedModel})
            """)
    int insertChunk(RagChunkEntity e);

    @Select("SELECT chunk_id AS chunkId, doc_id AS docId, kp_id AS kpId, chunk_index AS chunkIndex, heading, text, token_est AS tokenEst, embedding, embed_model AS embedModel FROM rag_chunk WHERE MATCH(text) AGAINST (#{q} IN NATURAL LANGUAGE MODE) LIMIT #{limit}")
    List<RagChunkEntity> fulltextSearch(@Param("q") String q, @Param("limit") int limit);

    @Select("SELECT chunk_id AS chunkId, doc_id AS docId, kp_id AS kpId, chunk_index AS chunkIndex, heading, text, token_est AS tokenEst, embedding, embed_model AS embedModel FROM rag_chunk LIMIT #{limit}")
    List<RagChunkEntity> selectChunksForScan(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM rag_chunk")
    int countChunks();

    @Select("SELECT COUNT(*) FROM rag_doc")
    int countDocs();
}
