-- ============================================================
-- 报告来源关联升级（存量环境专用，先备份，再用 MySQL 客户端执行）
-- MySQL version: 8.0.33
-- 不重建表、不改文件、不把推断逻辑放入运行时代码。
-- 仅回填物理路径及内外来源均唯一匹配的关联；末尾结果必须人工核对。
-- 本工具脚本不加入初始化/重置任务，已登记 ScriptTool 排除清单。
-- ============================================================
USE `znty_rrs`;
SET NAMES utf8mb4;

-- 幂等增加来源附件字段，保留已有数据。
SET @report_source_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = 'znty_rrs' AND table_name = 'sys_attachment'
       AND column_name = 'source_attachment_id') = 0,
    'ALTER TABLE sys_attachment ADD COLUMN source_attachment_id BIGINT DEFAULT NULL COMMENT ''报告库来源附件 ID，手工上传及报告库原件为空'' AFTER main_id',
    'SELECT ''source_attachment_id 已存在'' AS message'
);
PREPARE report_source_stmt FROM @report_source_ddl;
EXECUTE report_source_stmt;
DEALLOCATE PREPARE report_source_stmt;

-- 唯一来源才能回填；同一文件对应多份报告时不任意选取。
UPDATE sys_attachment a
JOIN (
    SELECT file_name, table_name, MIN(id) AS source_id
    FROM sys_attachment
    WHERE file_name IS NOT NULL AND file_name != ''
      AND ((table_name = 'rrs_report_in' AND attachment_category = 'report_in')
       OR (table_name = 'rrs_report_out' AND attachment_category = 'report_out'))
    GROUP BY file_name, table_name
    HAVING COUNT(*) = 1
) s ON s.file_name = a.file_name
    AND s.table_name = CASE
        WHEN a.attachment_category IN ('credit_report_in', 'material_in', 'fund_report_in', 'fund_material_in', 'stock_report_in', 'stock_material_in') THEN 'rrs_report_in'
        WHEN a.attachment_category IN ('credit_report_out', 'material_out', 'fund_report_out', 'fund_material_out', 'stock_report_out', 'stock_material_out') THEN 'rrs_report_out'
    END
SET a.source_attachment_id = s.source_id
WHERE a.table_name IN ('ip_adjust_log', 'ip_adjust_log_fund', 'ip_adjust_log_stock')
  AND a.source_attachment_id IS NULL;

-- 必须核对：有结果表示来源不唯一或原件缺失，应按实际报告修复来源 ID。
SELECT id, table_name, main_id, attachment_category, original_file_name
FROM sys_attachment
WHERE table_name IN ('ip_adjust_log', 'ip_adjust_log_fund', 'ip_adjust_log_stock')
  AND attachment_category IN ('credit_report_in', 'material_in', 'fund_report_in', 'fund_material_in', 'stock_report_in', 'stock_material_in', 'credit_report_out', 'material_out', 'fund_report_out', 'fund_material_out', 'stock_report_out', 'stock_material_out')
  AND source_attachment_id IS NULL AND is_deleted = 0
ORDER BY table_name, main_id, id;
