# 股票数据与调库脚本

股票基础与运行态独立于债券、基金。以下脚本供开发演示环境工具执行，本次实施不执行任何真实库建表、初始化或清空。

| 执行顺序 | 脚本 | 数据范围 |
|---|---|---|
| 1 | rrs_stockinfo_schema.sql | 股票基础信息、评级历史、多人分管 |
| 2 | rrs_stock_pool_adjust_schema.sql | 调库日志、审批步骤、当前池状态 |
| 3 | rrs_stockinfo_demo_data.sql | 14 只虚构股票，沪/深/港、评级边界、分管、退市、零值/空值 |
| 4 | rrs_stock_pool_adjust_demo_data.sql | 手工/联动/互斥、多池、各审核状态及自选样本 |

共同依赖：现有投资池、关系、开放日、权限、字典、审批流程（115～118）、AIS 用户角色、`my_security_pool`、`sys_attachment` 和报告表。股票品种使用 `a_share/hk_share`，市场使用 `SSE/SZSE/HKEX`，状态使用 `L/D/N`。

## 数据口径

六张表只保留主键，不设置物理外键或非主键索引，非主键允许 NULL，含创建/修改时间。评级为 buy/overweight/neutral/underweight/sell，按有效记录的评级日期、主键倒序取最近两次。

昨收/最高/最低/平均为 DECIMAL(20,6)，换手率 DECIMAL(10,6)，1.25 表示 1.25%。成交总量按来源提供的手数保存，港股不固定换算成 100 股；交易市值是当日成交金额（万），其他股本和市值按页面规定的百万保存，币种跟随市场。

日志保存提交时的名称、行业及最近两次评级，历史详情实时查询基础行情。股票自选以 `NOT EXISTS` 幂等追加到公共收藏表，不清空债券、基金或其他用户自选。

## 已注册的工具任务

| taskCode / moduleCode | 范围 |
|---|---|
| INIT_STOCK_SCHEMA | 依次重建六张股票表；先解除股票日志附件绑定 |
| INIT_STOCK_DEMO | 依次执行两份股票 Demo；先解除股票日志附件绑定 |
| CLEAR_STOCK_ADJUST_FLOW | 仅清空三张股票运行表并解除其附件绑定 |
| stock-info | 重置股票基础、评级、分管 Demo |
| stock-adjust | 重置股票运行态 Demo 并幂等追加自选 |

股票文件已接入脚本清单、模块重置、清空分组和健康检查；不参与公共 INIT_SCHEMA / INIT_DEMO / RESET_ALL 或基金任务。物理文件和公共沉淀报告不随股票运行态清空。

## 港股市场配置

股票港股池在原始 `rrs_pool_init_demo_data.sql` 的 INSERT 中直接使用 HKEX，不提供旧数据迁移脚本或迁移任务。

验收见 requirements/41～44；内存数据库测试见 StockPoolIntegrationTest，工具注册测试见 ScriptToolServiceTest。
