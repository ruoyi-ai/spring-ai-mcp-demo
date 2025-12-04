-- =========================================================
-- MCP 工具配置迁移 SQL 脚本
-- 功能：批量更新远程工具的传输类型
-- =========================================================

-- 查看当前配置（迁移前检查）
SELECT 
    id,
    name,
    type,
    status,
    JSON_EXTRACT(config_json, '$.transport.type') AS transport_type,
    config_json
FROM mcp_tool
WHERE type = 'REMOTE'
ORDER BY id;

-- =========================================================
-- 方案 1: 更新所有远程工具为 SSE 传输
-- =========================================================

-- 将所有 http/streamable-http 改为 sse
UPDATE mcp_tool
SET config_json = JSON_SET(
    config_json,
    '$.transport.type',
    'sse'
)
WHERE type = 'REMOTE'
  AND JSON_EXTRACT(config_json, '$.transport.type') IN ('http', 'streamable-http')
  AND config_json IS NOT NULL
  AND config_json != '';

-- =========================================================
-- 方案 2: 更新所有远程工具为 Streamable HTTP 传输
-- =========================================================

-- 将所有 http/sse 改为 streamable-http
-- UPDATE mcp_tool
-- SET config_json = JSON_SET(
--     config_json,
--     '$.transport.type',
--     'streamable-http'
-- )
-- WHERE type = 'REMOTE'
--   AND JSON_EXTRACT(config_json, '$.transport.type') IN ('http', 'sse')
--   AND config_json IS NOT NULL
--   AND config_json != '';

-- =========================================================
-- 方案 3: 按工具 ID 单独更新
-- =========================================================

-- 更新特定工具（示例：ID = 1）
-- UPDATE mcp_tool
-- SET config_json = JSON_SET(
--     config_json,
--     '$.transport.type',
--     'sse'
-- )
-- WHERE id = 1;

-- =========================================================
-- 方案 4: 完整替换配置（示例）
-- =========================================================

-- 如果 JSON_SET 不工作，可以完整替换配置
-- UPDATE mcp_tool
-- SET config_json = '{
--   "transport": {
--     "type": "sse",
--     "url": "http://localhost:9899"
--   }
-- }'
-- WHERE id = 1;

-- =========================================================
-- 验证更新结果
-- =========================================================

-- 查看更新后的配置
SELECT 
    id,
    name,
    type,
    status,
    JSON_EXTRACT(config_json, '$.transport.type') AS transport_type,
    JSON_EXTRACT(config_json, '$.transport.url') AS transport_url,
    config_json
FROM mcp_tool
WHERE type = 'REMOTE'
ORDER BY id;

-- 统计各传输类型的工具数量
SELECT 
    JSON_EXTRACT(config_json, '$.transport.type') AS transport_type,
    COUNT(*) AS tool_count
FROM mcp_tool
WHERE type = 'REMOTE'
  AND config_json IS NOT NULL
  AND config_json != ''
GROUP BY transport_type;

-- =========================================================
-- 回滚脚本（如果需要）
-- =========================================================

-- 将所有 sse 改回 streamable-http
-- UPDATE mcp_tool
-- SET config_json = JSON_SET(
--     config_json,
--     '$.transport.type',
--     'streamable-http'
-- )
-- WHERE type = 'REMOTE'
--   AND JSON_EXTRACT(config_json, '$.transport.type') = 'sse';

