-- learning_loop schema v1
-- 问题：state.json 全量写放大（dumps 19ms + write 51ms/次）；无法 SQL 聚合
-- 决策：MySQL 8 InnoDB 行存；utf8mb4；DATE 业务日；DATETIME(3) 时间戳
-- 范围：个人单机，不做分表/读写分离

CREATE DATABASE IF NOT EXISTS learning_loop
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE learning_loop;

CREATE TABLE IF NOT EXISTS kp (
  id            VARCHAR(64)  NOT NULL,
  domain        VARCHAR(64)  NOT NULL,
  chapter       VARCHAR(32)  NOT NULL DEFAULT '',
  title         VARCHAR(255) NOT NULL,
  difficulty    TINYINT      NOT NULL DEFAULT 3,
  hot           TINYINT      NOT NULL DEFAULT 3,
  category      VARCHAR(16)  NOT NULL,
  path          VARCHAR(512) NOT NULL DEFAULT '',
  kb_status     VARCHAR(16)  NOT NULL DEFAULT 'done',
  is_lc         TINYINT(1)   NOT NULL DEFAULT 0,
  meta_json     JSON         NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_kp_domain (domain),
  KEY idx_kp_cat (category, is_lc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kp_state (
  kp_id            VARCHAR(64)  NOT NULL,
  status           VARCHAR(16)  NOT NULL DEFAULT 'new',
  ease             DECIMAL(4,2) NOT NULL DEFAULT 2.50,
  interval_days    INT          NOT NULL DEFAULT 0,
  due_date         DATE         NULL,
  stability        DECIMAL(8,2) NOT NULL DEFAULT 0,
  lapses           INT          NOT NULL DEFAULT 0,
  last_score       TINYINT      NULL,
  reviews          INT          NOT NULL DEFAULT 0,
  last_review_date DATE         NULL,
  plan_json        JSON         NULL,
  version          BIGINT       NOT NULL DEFAULT 0,
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (kp_id),
  KEY idx_kp_state_due (due_date, status),
  CONSTRAINT fk_kp_state_kp FOREIGN KEY (kp_id) REFERENCES kp(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kp_history (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  kp_id       VARCHAR(64)  NOT NULL,
  review_date DATE         NOT NULL,
  score       TINYINT      NOT NULL,
  q           TINYINT      NOT NULL,
  stability   DECIMAL(8,2) NOT NULL DEFAULT 0,
  KEY idx_hist_kp_date (kp_id, review_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question_hist (
  kp_id        VARCHAR(64)  NOT NULL,
  question_id  VARCHAR(64)  NOT NULL,
  question     TEXT         NOT NULL,
  answer_points JSON        NULL,
  point        VARCHAR(64)  NOT NULL DEFAULT '',
  score        TINYINT      NOT NULL DEFAULT 0,
  tries        INT          NOT NULL DEFAULT 1,
  due_date     DATE         NULL,
  last_at      DATETIME(3)  NULL,
  PRIMARY KEY (kp_id, question_id),
  KEY idx_qh_due (due_date, score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS answer_record (
  id            VARCHAR(64)  NOT NULL,
  kp_id         VARCHAR(64)  NOT NULL,
  question_id   VARCHAR(64)  NOT NULL,
  question      TEXT         NOT NULL,
  answer_text   MEDIUMTEXT   NULL,
  code          MEDIUMTEXT   NULL,
  think         MEDIUMTEXT   NULL,
  spoken        TINYINT(1)   NOT NULL DEFAULT 0,
  score         TINYINT      NOT NULL DEFAULT 0,
  level         VARCHAR(16)  NOT NULL DEFAULT '',
  verdict       VARCHAR(16)  NOT NULL DEFAULT '',
  profile       JSON         NULL,
  diagnose_json JSON         NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_rec_time (created_at),
  KEY idx_rec_kp_time (kp_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gap (
  id          VARCHAR(64)  NOT NULL,
  kp_id       VARCHAR(64)  NOT NULL,
  label       VARCHAR(32)  NOT NULL,
  description TEXT NOT NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  resolved_at DATE         NULL,
  PRIMARY KEY (id),
  KEY idx_gap_open (resolved_at, label),
  KEY idx_gap_kp (kp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS session_day (
  session_date DATE         NOT NULL,
  cnt          INT          NOT NULL DEFAULT 0,
  avg_score    INT          NOT NULL DEFAULT 0,
  new_g        INT          NOT NULL DEFAULT 0,
  res_g        INT          NOT NULL DEFAULT 0,
  min_score    INT          NOT NULL DEFAULT 0,
  spoken       TINYINT(1)   NOT NULL DEFAULT 0,
  mode         VARCHAR(16)  NOT NULL DEFAULT 'standard',
  PRIMARY KEY (session_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS queue_item (
  id          VARCHAR(64)  NOT NULL,
  kp_id       VARCHAR(64)  NOT NULL,
  kind        VARCHAR(16)  NOT NULL,
  reason      VARCHAR(255) NOT NULL DEFAULT '',
  questions   JSON         NOT NULL,
  created_on  DATE         NOT NULL,
  consumed_at DATETIME(3)  NULL,
  PRIMARY KEY (id),
  KEY idx_queue_day (created_on, consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_setting (
  k          VARCHAR(64) NOT NULL,
  v          JSON        NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS idem_key (
  idem_key   VARCHAR(128) NOT NULL,
  scope      VARCHAR(64)  NOT NULL,
  response   JSON         NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (idem_key),
  KEY idx_idem_exp (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_doc (
  doc_id       VARCHAR(128) NOT NULL,
  kp_id        VARCHAR(64)  NULL,
  domain       VARCHAR(64)  NOT NULL,
  title        VARCHAR(255) NOT NULL,
  content_hash CHAR(64)     NOT NULL,
  chunk_total  INT          NOT NULL DEFAULT 0,
  embedded_at  DATETIME(3)  NULL,
  PRIMARY KEY (doc_id),
  KEY idx_rag_domain (domain),
  KEY idx_rag_kp (kp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_chunk (
  chunk_id     VARCHAR(128) NOT NULL,
  doc_id       VARCHAR(128) NOT NULL,
  kp_id        VARCHAR(64)  NULL,
  chunk_index  INT          NOT NULL,
  heading      VARCHAR(255) NOT NULL DEFAULT '',
  text         MEDIUMTEXT   NOT NULL,
  token_est    INT          NOT NULL DEFAULT 0,
  embedding    BLOB         NULL,
  embed_model  VARCHAR(64)  NOT NULL DEFAULT '',
  PRIMARY KEY (chunk_id),
  KEY idx_chunk_doc (doc_id, chunk_index),
  KEY idx_chunk_kp (kp_id),
  FULLTEXT KEY ft_chunk_text (text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_run (
  run_id        VARCHAR(64)  NOT NULL,
  scene         VARCHAR(32)  NOT NULL,
  trace_id      VARCHAR(64)  NOT NULL,
  status        VARCHAR(16)  NOT NULL,
  latency_ms    INT          NOT NULL DEFAULT 0,
  tokens_in     INT          NOT NULL DEFAULT 0,
  tokens_out    INT          NOT NULL DEFAULT 0,
  input_digest  VARCHAR(512) NOT NULL DEFAULT '',
  output_digest VARCHAR(512) NOT NULL DEFAULT '',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (run_id),
  KEY idx_agent_scene_time (scene, created_at),
  KEY idx_agent_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
