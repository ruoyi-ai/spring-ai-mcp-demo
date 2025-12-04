-- =========================================================
-- MCP 工具表 DDL
-- 数据库: MySQL 8.0+
-- =========================================================

-- =========================================================
-- 方案 1: 创建新表（全新环境）
-- =========================================================

CREATE TABLE IF NOT EXISTS `mcp_tool` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '工具名称（唯一标识）',
    `display_name` VARCHAR(200) DEFAULT NULL COMMENT '显示名称',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '工具描述',
    `param_schema` TEXT DEFAULT NULL COMMENT '参数结构（JSON Schema 格式）',
    `type` VARCHAR(20) NOT NULL DEFAULT 'LOCAL' COMMENT '工具类型：LOCAL-本地, REMOTE-远程',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-禁用',
    `config_json` TEXT DEFAULT NULL COMMENT '配置信息（JSON格式，包含transport等）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`),
    KEY `idx_type_status` (`type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 工具表';


-- =========================================================
-- 方案 2: 更新现有表（已有表结构，添加缺失字段）
-- =========================================================

-- 添加 display_name 字段
ALTER TABLE `mcp_tool` 
ADD COLUMN `display_name` VARCHAR(200) DEFAULT NULL COMMENT '显示名称' 
AFTER `name`;

-- 添加 param_schema 字段
ALTER TABLE `mcp_tool` 
ADD COLUMN `param_schema` TEXT DEFAULT NULL COMMENT '参数结构（JSON Schema 格式）' 
AFTER `description`;

-- 添加 name 唯一索引（如果不存在）
ALTER TABLE `mcp_tool` 
ADD UNIQUE INDEX `uk_name` (`name`);


-- =========================================================
-- 方案 3: 安全更新（先检查字段是否存在）
-- =========================================================

-- 检查并添加 display_name 字段
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'mcp_tool' 
    AND COLUMN_NAME = 'display_name'
);

SET @sql = IF(@column_exists = 0, 
    'ALTER TABLE mcp_tool ADD COLUMN display_name VARCHAR(200) DEFAULT NULL COMMENT ''显示名称'' AFTER name', 
    'SELECT ''display_name column already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 param_schema 字段
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'mcp_tool' 
    AND COLUMN_NAME = 'param_schema'
);

SET @sql = IF(@column_exists = 0, 
    'ALTER TABLE mcp_tool ADD COLUMN param_schema TEXT DEFAULT NULL COMMENT ''参数结构（JSON Schema 格式）'' AFTER description', 
    'SELECT ''param_schema column already exists'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =========================================================
-- 验证表结构
-- =========================================================

-- 查看表结构
DESC mcp_tool;

-- 查看完整的建表语句
SHOW CREATE TABLE mcp_tool;


-- =========================================================
-- 预期的完整表结构
-- =========================================================
/*
+-------------+--------------+------+-----+-------------------+-----------------------------------------------+
| Field       | Type         | Null | Key | Default           | Extra                                         |
+-------------+--------------+------+-----+-------------------+-----------------------------------------------+
| id          | bigint       | NO   | PRI | NULL              | auto_increment                                |
| name        | varchar(100) | NO   | UNI | NULL              |                                               |
| display_name| varchar(200) | YES  |     | NULL              |                                               |
| description | varchar(1000)| YES  |     | NULL              |                                               |
| param_schema| text         | YES  |     | NULL              |                                               |
| type        | varchar(20)  | NO   |     | LOCAL             |                                               |
| status      | varchar(20)  | NO   |     | ENABLED           |                                               |
| config_json | text         | YES  |     | NULL              |                                               |
| create_time | datetime     | YES  |     | CURRENT_TIMESTAMP |                                               |
| update_time | datetime     | YES  |     | CURRENT_TIMESTAMP | on update CURRENT_TIMESTAMP                   |
+-------------+--------------+------+-----+-------------------+-----------------------------------------------+
*/


-- =========================================================
-- 字段说明
-- =========================================================
/*
| 字段名       | 类型          | 说明                                      | 示例值                                    |
|-------------|---------------|------------------------------------------|------------------------------------------|
| id          | BIGINT        | 主键，自增                                | 1, 2, 3...                               |
| name        | VARCHAR(100)  | 工具名称，唯一标识                        | get_current_time, calculator_add         |
| display_name| VARCHAR(200)  | 显示名称（用于前端展示）                  | 获取当前时间, 加法计算                    |
| description | VARCHAR(1000) | 工具描述                                  | 获取当前系统时间，支持多种格式            |
| param_schema| TEXT          | 参数 JSON Schema                          | {"type":"object","properties":{...}}      |
| type        | VARCHAR(20)   | 工具类型                                  | LOCAL / REMOTE                           |
| status      | VARCHAR(20)   | 工具状态                                  | ENABLED / DISABLED                       |
| config_json | TEXT          | 配置信息（远程工具需要）                  | {"transport":{"type":"sse","url":"..."}} |
| create_time | DATETIME      | 创建时间                                  | 2024-12-04 10:00:00                      |
| update_time | DATETIME      | 更新时间（自动更新）                      | 2024-12-04 15:30:00                      |
*/


-- =========================================================
-- 示例数据（可选）
-- =========================================================

-- 插入本地工具示例
INSERT INTO `mcp_tool` (`name`, `display_name`, `description`, `type`, `status`, `param_schema`, `config_json`) 
VALUES 
('get_current_time', '获取当前时间', '获取当前系统时间，支持多种时间格式', 'LOCAL', 'ENABLED', 
 '{"type":"object","properties":{"format":{"type":"string","description":"时间格式"}}}', 
 '{}');

-- 插入远程工具示例
INSERT INTO `mcp_tool` (`name`, `display_name`, `description`, `type`, `status`, `param_schema`, `config_json`) 
VALUES 
('calculator_add', '加法计算', '计算两个数的和', 'REMOTE', 'ENABLED', 
 '{"type":"object","properties":{"a":{"type":"number"},"b":{"type":"number"}},"required":["a","b"]}', 
 '{"transport":{"type":"sse","url":"http://localhost:9899"}}');


-- =========================================================
-- 数据迁移（如果需要从旧表迁移）
-- =========================================================

-- 将 display_name 初始化为 name 的值（如果 display_name 为空）
UPDATE `mcp_tool` 
SET `display_name` = `name` 
WHERE `display_name` IS NULL OR `display_name` = '';

