-- Deepay v3 生产版数据库迁移脚本
-- 执行顺序：1 → 2 → 3

-- ============================================================
-- 1. deepay_style_chain（设计链）—— 新增字段
-- ============================================================
ALTER TABLE deepay_style_chain
    ADD COLUMN IF NOT EXISTS keyword        VARCHAR(256)  DEFAULT NULL COMMENT '用户输入关键词',
    ADD COLUMN IF NOT EXISTS selected_image VARCHAR(512)  DEFAULT NULL COMMENT 'AI 决策选中的图片',
    ADD COLUMN IF NOT EXISTS pattern_file   VARCHAR(512)  DEFAULT NULL COMMENT '打版文件路径',
    ADD COLUMN IF NOT EXISTS decision_reason VARCHAR(1024) DEFAULT NULL COMMENT 'AI 决策原因';

-- ============================================================
-- 2. deepay_product（商品表）—— 全新创建
-- ============================================================
CREATE TABLE IF NOT EXISTS deepay_product (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    chain_code VARCHAR(64)  NOT NULL                COMMENT '关联链码',
    title      VARCHAR(512) NOT NULL DEFAULT ''     COMMENT '商品标题',
    price      DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '售价（元）',
    status     VARCHAR(32)  NOT NULL DEFAULT 'SELLING' COMMENT '状态：SELLING / STOPPED / REDESIGNING',
    sold_count INT          NOT NULL DEFAULT 0      COMMENT '销量',
    stock      INT          NOT NULL DEFAULT 0      COMMENT '可用库存',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chain_code (chain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Deepay 商品表';

-- ============================================================
-- 3. deepay_inventory（库存表）—— 全新创建
-- ============================================================
CREATE TABLE IF NOT EXISTS deepay_inventory (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    chain_code  VARCHAR(64) NOT NULL                COMMENT '关联链码',
    stock       INT         NOT NULL DEFAULT 0      COMMENT '可用库存',
    locked_stock INT        NOT NULL DEFAULT 0      COMMENT '锁定库存',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chain_code (chain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Deepay 库存表';

-- ============================================================
-- 4. deepay_metrics（指标表）—— 全新创建
-- ============================================================
CREATE TABLE IF NOT EXISTS deepay_metrics (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    chain_code VARCHAR(64)   NOT NULL                COMMENT '关联链码',
    sold_count INT           NOT NULL DEFAULT 0      COMMENT '销量快照',
    price      DECIMAL(10,2)         DEFAULT NULL    COMMENT '上架价格快照',
    category   VARCHAR(128)          DEFAULT NULL    COMMENT '分类（来自 keyword）',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chain_code (chain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Deepay 销售指标表';
