-- 阶段 1.3：业务表加 version 字段（按文档 §八 V3）
-- 用于冲突副本同步的乐观锁

ALTER TABLE knowledge_entries ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
