-- =====================================================================
-- Deepay 第二阶段 ima 知识库字段迁移
-- 在已有 deepay_style_chain 表上执行
-- =====================================================================

-- 仅在列不存在时添加，防止重复执行报错
ALTER TABLE `deepay_style_chain`
    ADD COLUMN IF NOT EXISTS `ima_kb_id` VARCHAR(128) DEFAULT NULL COMMENT 'ima 知识库 ID，同步失败时为 NULL'
        AFTER `created_at`;
