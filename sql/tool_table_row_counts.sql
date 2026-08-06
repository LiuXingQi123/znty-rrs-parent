-- ============================================================
-- 表记录数统计工具（只读查询）
-- 用途：查看各业务库每张表的记录数（精确 COUNT(*)）
-- 覆盖库：znty_rrs（主业务库）/ ais_inv_analysis（AIS 投资分析）/ ais_inv_ods（Wind ODS）
-- 输出字段：表名称、数据条数
-- 使用方式：在 MySQL 客户端（mysql cli / Navicat / DBeaver 等）逐段执行
--           每段返回对应库所有表的「表名称 + 数据条数」
-- 实现说明：用 information_schema.TABLES 动态拼接 UNION ALL COUNT(*) 语句后 PREPARE 执行，
--           精确统计；不依赖 DELIMITER / 存储过程，各客户端通用。
-- 表清单策略：
--   znty_rrs：统计该库全部基础表（本地即全部业务表）。
--   ais_inv_analysis / ais_inv_ods：公司项目里这两个库表很多，仅统计本地已有的表
--     （以 TABLE_NAME IN (...) 显式指定），如需增减表请同步修改清单。
-- 注意：本脚本为只读查询工具，不参与 ScriptTool 初始化（非建表 / Demo 脚本，未注册）。
--       如需快速估算（InnoDB 近似值），可改用：
--         SELECT TABLE_NAME AS 表名称, TABLE_ROWS AS 数据条数
--         FROM information_schema.TABLES
--         WHERE TABLE_SCHEMA = 'znty_rrs' AND TABLE_TYPE = 'BASE TABLE';
-- MySQL version: 8.0.33
-- ============================================================

-- 提高 GROUP_CONCAT 长度上限，避免拼接多表 UNION ALL 时被截断（默认 1024 远不够）
SET SESSION group_concat_max_len = 1000000;

-- ============================================================
-- 1. znty_rrs 主业务库
-- ============================================================
SET @sql = NULL;
SELECT GROUP_CONCAT(
    CONCAT('SELECT ''', TABLE_NAME, ''' AS `表名称`, COUNT(*) AS `数据条数` FROM `', TABLE_SCHEMA, '`.`', TABLE_NAME, '`')
    SEPARATOR ' UNION ALL '
) INTO @sql
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'znty_rrs'
  AND TABLE_TYPE = 'BASE TABLE';
-- 库无表时兜底，避免 PREPARE NULL 报错
SET @sql = IFNULL(@sql, 'SELECT NULL AS `表名称`, 0 AS `数据条数` WHERE 1 = 0');
SET @sql = CONCAT(@sql, ' ORDER BY `数据条数` DESC');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. ais_inv_analysis AIS 投资分析库
-- ============================================================
SET @sql = NULL;
SELECT GROUP_CONCAT(
    CONCAT('SELECT ''', TABLE_NAME, ''' AS `表名称`, COUNT(*) AS `数据条数` FROM `', TABLE_SCHEMA, '`.`', TABLE_NAME, '`')
    SEPARATOR ' UNION ALL '
) INTO @sql
FROM information_schema.TABLES
-- 仅统计本地已有表（公司库表多，显式指定清单，如需增减请同步修改）
WHERE TABLE_SCHEMA = 'ais_inv_analysis'
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME IN (
      't_inv_company',
      't_inv_grade_result',
      't_sys_role',
      't_sys_role_evt',
      't_sys_user',
      't_sys_user_evt',
      't_sys_user_role',
      't_sys_user_role_evt'
  );
SET @sql = IFNULL(@sql, 'SELECT NULL AS `表名称`, 0 AS `数据条数` WHERE 1 = 0');
SET @sql = CONCAT(@sql, ' ORDER BY `数据条数` DESC');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. ais_inv_ods AIS 投资 ODS 库
-- ============================================================
SET @sql = NULL;
SELECT GROUP_CONCAT(
    CONCAT('SELECT ''', TABLE_NAME, ''' AS `表名称`, COUNT(*) AS `数据条数` FROM `', TABLE_SCHEMA, '`.`', TABLE_NAME, '`')
    SEPARATOR ' UNION ALL '
) INTO @sql
FROM information_schema.TABLES
-- 仅统计本地已有表（公司库表多，显式指定清单，如需增减请同步修改）
WHERE TABLE_SCHEMA = 'ais_inv_ods'
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME IN (
      'wind_cbondissuer',
      'wind_cbondissuerrating'
  );
SET @sql = IFNULL(@sql, 'SELECT NULL AS `表名称`, 0 AS `数据条数` WHERE 1 = 0');
SET @sql = CONCAT(@sql, ' ORDER BY `数据条数` DESC');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
