-- =====================================================================
-- Deepay 一衣一链 MVP —— 数据库初始化脚本
-- 执行环境：MySQL 5.7 / 8.0
-- =====================================================================

-- ----------------------------
-- Table structure for deepay_style_chain
-- ----------------------------
CREATE TABLE IF NOT EXISTS `deepay_style_chain`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `chain_code` VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '6位随机大写链码，全局唯一',
    `image_url`  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '最终选中的设计图片URL',
    `status`     VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '状态：CREATED',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_chain_code` (`chain_code`) COMMENT '链码唯一索引'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'Deepay 样式链码表';
