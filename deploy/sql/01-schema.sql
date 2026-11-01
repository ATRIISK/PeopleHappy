-- =============================================
-- 众乐电子商城 - 建表脚本（8 张表）
-- 来源：docs/design/众乐电子商城-开发文档.md 第 4 章
-- 用途：MySQL 容器首次启动自动执行（docker-entrypoint-initdb.d）
-- 注意：order / user 是 MySQL 保留字，必须加反引号
-- =============================================

SET NAMES utf8mb4;

-- ===== 4.1 用户表 =====
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `username`    VARCHAR(50)  NOT NULL UNIQUE,
    `password`    VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密',
    `phone`       VARCHAR(20),
    `avatar`      VARCHAR(255),
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '账号状态: 0-正常 1-禁用',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ===== 4.2 分类表 =====
CREATE TABLE IF NOT EXISTS `category` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(50) NOT NULL,
    `parent_id`   BIGINT      DEFAULT NULL COMMENT '父分类ID，0=一级分类',
    `level`       TINYINT     DEFAULT 1 COMMENT '层级：1=一级 2=二级',
    `sort`        INT         DEFAULT 0 COMMENT '排序序号',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME,
    `is_deleted`  TINYINT     DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- ===== 4.3 商品表 =====
CREATE TABLE IF NOT EXISTS `product` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(200)   NOT NULL,
    `description`    TEXT,
    `price`          DECIMAL(10,2)  NOT NULL COMMENT '现价',
    `original_price` DECIMAL(10,2)  COMMENT '原价（划线价）',
    `image`          VARCHAR(255)   COMMENT '商品主图',
    `images`         TEXT           COMMENT '多图 JSON 数组',
    `category_id`    BIGINT         NOT NULL,
    `category_name`  VARCHAR(50)    COMMENT '分类名称（冗余字段）',
    `sales`          INT            NOT NULL DEFAULT 0,
    `stock`          INT            NOT NULL DEFAULT 0,
    `rating`         DOUBLE         DEFAULT 0 COMMENT '评分 0-5',
    `status`         TINYINT        NOT NULL DEFAULT 0 COMMENT '0上架 1下架',
    `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME,
    `is_deleted`     TINYINT        DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- ===== 4.4 购物车表 =====
CREATE TABLE IF NOT EXISTS `cart` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL,
    `product_id`  BIGINT   NOT NULL,
    `quantity`    INT      NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- ===== 4.5 订单表 =====
CREATE TABLE IF NOT EXISTS `order` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `order_no`       VARCHAR(32)   NOT NULL UNIQUE COMMENT '订单号（18位）',
    `user_id`        BIGINT        NOT NULL,
    `total_amount`   DECIMAL(10,2) NOT NULL,
    `status`         TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已发货 3已完成 4已取消 5已退款',
    `address_id`     BIGINT,
    `transaction_id` VARCHAR(64)   COMMENT '支付宝交易号（trade_no）',
    `prepay_id`      VARCHAR(64)   COMMENT '预支付ID（预留）',
    `pay_time`       DATETIME,
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ===== 4.6 订单项表 =====
CREATE TABLE IF NOT EXISTS `order_item` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT,
    `order_id`   BIGINT        NOT NULL,
    `product_id` BIGINT        NOT NULL,
    `quantity`   INT           NOT NULL,
    `price`      DECIMAL(10,2) NOT NULL COMMENT '下单时价格快照',
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';

-- ===== 4.7 收货地址表 =====
CREATE TABLE IF NOT EXISTS `address` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL,
    `name`       VARCHAR(50)  NOT NULL COMMENT '收件人',
    `phone`      VARCHAR(20)  NOT NULL,
    `province`   VARCHAR(50)  NOT NULL,
    `city`       VARCHAR(50)  NOT NULL,
    `district`   VARCHAR(50),
    `detail`     VARCHAR(200) NOT NULL,
    `is_default` TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

-- ===== 4.8 支付流水表 =====
CREATE TABLE IF NOT EXISTS `payment_log` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `order_no`       VARCHAR(32) NOT NULL,
    `transaction_id` VARCHAR(64),
    `pay_type`       VARCHAR(16) COMMENT 'PRECREATE（支付宝扫码支付）',
    `total_fee`      INT         COMMENT '金额单位：分',
    `trade_state`    VARCHAR(32) COMMENT 'WAIT_BUYER_PAY/TRADE_SUCCESS/TRADE_FINISHED/TRADE_CLOSED',
    `notify_raw`     TEXT        COMMENT '支付宝异步通知原始报文',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水表';
