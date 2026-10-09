package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.*;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.mymatters.MyMattersDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import com.znty.rrs.entity.stockpooladjust.*;
import com.znty.rrs.entity.stockpooladjusthistory.*;
import com.znty.rrs.entity.stockpoolquery.*;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 Mapper、内存事务和主行锁验证股票申请、审批、查询及附件完整链路。 */
public class StockPoolIntegrationTest {
    /** 独立内存数据库。 */
    private JdbcTemplate jdbc;
    /** 事务管理器，所有写入通过服务代理提交。 */
    private DataSourceTransactionManager transactions;
    /** 真实股票调整 Mapper。 */
    private StockPoolAdjustMapper adjustments;
    /** 真实个人及公共股票服务。 */
    private StockPoolQueryService queries;
    /** 真实股票历史查询服务。 */
    private StockPoolAdjustHistoryService history;
    /** 真实申请服务。 */
    private StockPoolAdjustService apply;
    /** 真实审批服务。 */
    private StockPoolAdjustFlowService audit;
    /** 真实公共附件服务。 */
    private SysAttachmentService attachments;
    /** 股票事宜服务。 */
    private StockMyMattersService matters;
    /** 流程配置使用受控节点，业务 Mapper 和事务均为真实实现。 */
    private FlowMapper flows;
    /** 内存 Mapper 会话模板。 */
    private SqlSessionTemplate session;
    /** 所有审批处理人，测试会签时增加第二位。 */
    private List<NodeApprovalHandlerBo> handlers;

    /** 创建六张股票表及依赖的公共表，仅在 H2 中执行 DDL。 */
    @Before public void setUp() throws Exception {
        // 防止使用模拟 Mapper 的其他测试遗留分页线程状态。
        PageHelper.clearPage();
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:stock" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        Configuration config = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        PageInterceptor paging = new PageInterceptor();
        Properties properties = new Properties(); properties.setProperty("helperDialect", "h2");
        paging.setProperties(properties); config.addInterceptor(paging);
        for (String name : Arrays.asList("StockPoolAdjust", "StockPoolQuery", "StockPoolAdjustHistory", "StockMyMatters",
                "InvestmentPool", "MySecurityPool", "SysAttachment", "Report")) {
            String resource = "mapper/" + name + "Mapper.xml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
            }
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        session = new SqlSessionTemplate(factory);
        // 从真正的股票 DDL 提取建表，排除真实数据库 USE、清空及 MySQL 表选项。
        for (String name : Arrays.asList("stock/rrs_stockinfo_schema.sql", "stock/rrs_stock_pool_adjust_schema.sql",
                "rrs_pool_init_schema.sql", "rrs_my_security_pool_schema.sql", "rrs_sys_attachment_schema.sql")) {
            String ddl = new String(Files.readAllBytes(Paths.get("sql", name)), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("(?is)CREATE TABLE.*?;").matcher(ddl);
            while (matcher.find()) {
                String table = matcher.group().replaceAll("(?is)\\)\\s*ENGINE\\s*=.*", ")")
                        .replaceAll("(?i)\\bJSON\\b", "VARCHAR(4000)");
                jdbc.execute(table);
            }
        }
        sql("CREATE TABLE dict_security_type(security_type VARCHAR(32),security_type_name VARCHAR(60),category_type VARCHAR(20),sort_order INT,is_deleted INT)",
                "INSERT INTO dict_security_type VALUES('stock_a','A股','stock',1,0),('fund','基金','fund',2,0)",
                "CREATE SCHEMA ais_inv_analysis",
                "CREATE TABLE ais_inv_analysis.t_sys_user_role(user_id BIGINT,role_id BIGINT)",
                "CREATE TABLE ais_inv_analysis.t_sys_role(id BIGINT,enable INT)",
                "CREATE TABLE wf_flow_definition(id BIGINT PRIMARY KEY,flow_key VARCHAR(40),name VARCHAR(40),description VARCHAR(40),is_deleted INT)",
                "CREATE TABLE wf_flow_node(id BIGINT PRIMARY KEY,flow_id BIGINT)",
                "INSERT INTO wf_flow_definition VALUES(10,'stock_normal','股票一般','股票',0),(20,'stock_fast','股票快速','股票',0)",
                "INSERT INTO wf_flow_node VALUES(101,10),(102,10),(103,10),(104,10),(105,10),(201,20),(202,20),(205,20)");
        stock("600001.SH"); stock("600002.SH"); stock("600003.SH");
        for (int id=1; id<=3; id++) {
            jdbc.update("INSERT INTO ip_investment_pool(id,pool_name,pool_code,pool_type,market_codes,variety_codes,status,is_deleted,in_flow_id,in_flow_key,out_flow_id,out_flow_key,simple_in_flow_id,simple_in_flow_key,simple_out_flow_id,simple_out_flow_key,in_report_restriction,out_report_restriction) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,"股票池"+id,"stock_"+id,"stock","[\"SSE\"]","[\"stock\"]","enabled",0,10,"stock_normal",10,"stock_normal",20,"stock_fast",20,"stock_fast","none","none");
        }
        adjustments = session.getMapper(StockPoolAdjustMapper.class);
        InvestmentPoolMapper pools = session.getMapper(InvestmentPoolMapper.class);
        InvestmentPoolService paths = mock(InvestmentPoolService.class);
        Map<Long,String> names = new HashMap<>(); names.put(1L,"股票池1"); names.put(2L,"股票池2"); names.put(3L,"股票池3");
        when(paths.queryPoolFullNameMap()).thenReturn(names);
        attachments = new SysAttachmentService();
        ReflectionTestUtils.setField(attachments,"sysAttachmentMapper",session.getMapper(SysAttachmentMapper.class));
        ReportService reports = new ReportService();
        ReflectionTestUtils.setField(reports,"reportMapper",session.getMapper(ReportMapper.class));
        ReflectionTestUtils.setField(reports,"sysAttachmentService",attachments);
        ReflectionTestUtils.setField(reports,"investmentPoolService",paths);
        flows = mock(FlowMapper.class); handlers = new ArrayList<>();
        configureFlow(10L,false,"preempt"); configureFlow(20L,true,"preempt");
        apply = new StockPoolAdjustService();
        ReflectionTestUtils.setField(apply,"stockPoolAdjustMapper",adjustments);
        ReflectionTestUtils.setField(apply,"investmentPoolMapper",pools);
        ReflectionTestUtils.setField(apply,"investmentPoolService",paths);
        ReflectionTestUtils.setField(apply,"flowMapper",flows);
        ReflectionTestUtils.setField(apply,"sysAttachmentService",attachments);
        ReflectionTestUtils.setField(apply,"reportService",reports);
        apply = transactional(apply);
        audit = new StockPoolAdjustFlowService();
        ReflectionTestUtils.setField(audit,"stockPoolAdjustMapper",adjustments);
        ReflectionTestUtils.setField(audit,"flowMapper",flows);
        ReflectionTestUtils.setField(audit,"stockPoolAdjustService",apply);
        ReflectionTestUtils.setField(audit,"sysAttachmentService",attachments);
        ReflectionTestUtils.setField(audit,"investmentPoolMapper",pools);
        audit = transactional(audit);
        queries = new StockPoolQueryService();
        ReflectionTestUtils.setField(queries,"stockPoolQueryMapper",session.getMapper(StockPoolQueryMapper.class));
        ReflectionTestUtils.setField(queries,"mySecurityPoolMapper",session.getMapper(MySecurityPoolMapper.class));
        ReflectionTestUtils.setField(queries,"investmentPoolService",paths);
        queries = transactional(queries);
        history = new StockPoolAdjustHistoryService();
        ReflectionTestUtils.setField(history,"stockPoolAdjustHistoryMapper",session.getMapper(StockPoolAdjustHistoryMapper.class));
        ReflectionTestUtils.setField(history,"investmentPoolService",paths);
        matters = new StockMyMattersService();
        ReflectionTestUtils.setField(matters,"myMattersMapper",session.getMapper(StockMyMattersMapper.class));
        ReflectionTestUtils.setField(matters,"investmentPoolService",paths);
    }

    /** 一般申请仅在终审生效，事宜按处理人分派，名称行业评级留痕不随基础变化。 */
    @Test public void normalFlowShouldReachMattersQueryHistoryAndReports() throws Exception {
        sql("INSERT INTO rrs_stock_rating(stock_code,rating_code,rating_date,is_deleted) VALUES('600001.SH','buy','2026-10-01',0)");
        StockAdjustSubmitDto submitted = apply.addAdjustLog(request("600001.SH", false, 1L));
        assertThat(count("ip_pool_status_stock")).isZero();
        MyMattersReq matter = new MyMattersReq(); matter.setBusinessDomain("stock"); matter.setCurrentUserId("5"); matter.setStepStatus("pending");
        PageResult<MyMattersDto> pending = matters.queryMyMattersPage(matter);
        assertThat(pending.getRecords()).hasSize(1);
        assertThat(pending.getRecords().get(0).getBusinessScene()).isEqualTo("stockAdjust");
        assertThat(pending.getRecords().get(0).getStockCode()).isEqualTo("600001.SH");
        Long logId = submitted.getAdjustLogIds().get(0);
        attachment(8L,"ip_adjust_log_stock",logId,"stock_report_hand");
        audit.submitAdjustAudit(auditReq(submitted,"5","approve"));
        assertThat(count("ip_pool_status_stock")).isEqualTo(1);
        assertThat(count("rrs_report_in")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT security_type FROM rrs_report_in",String.class)).isEqualTo("stock");
        sql("UPDATE rrs_stockinfo SET stock_name='实时新名称',industry_name='实时新行业' WHERE stock_code='600001.SH'",
                "INSERT INTO rrs_stock_rating(stock_code,rating_code,rating_date,is_deleted) VALUES('600001.SH','sell','2026-10-08',0)");
        StockPoolQueryDto current = queries.queryStockPoolPage(query()).getRecords().get(0);
        assertThat(current.getLatestRating()).isEqualTo("sell"); assertThat(current.getPreviousRating()).isEqualTo("buy");
        assertThat(current.getStockName()).isEqualTo("实时新名称"); assertThat(current.getAdjustLogId()).isEqualTo(logId);
        StockPoolAdjustHistoryDto frozen = history.queryStockPoolAdjustHistoryPage(new StockPoolAdjustHistoryReq()).getRecords().get(0);
        assertThat(frozen.getIndustryName()).isEqualTo("林业"); assertThat(frozen.getLatestRating()).isEqualTo("buy");
        assertThat(frozen.getStockName()).isEqualTo("股票600001.SH");
    }

    /** 快速主项与联动关系全部保存后一起置为 20，调出同步移除整组成员。 */
    @Test public void fastFlowShouldApplyWholeLinkedGroupAndOutbound() {
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'in_linked',2,0)");
        StockPoolAdjustSubmitReq req = request("600001.SH",true,1L);
        StockPoolAdjustSubmitReq.AdjustItem linked = item(2L,"linkage",true,"调入");
        req.getItems().add(linked);
        StockAdjustSubmitDto result = apply.addAdjustLog(req);
        assertThat(result.getAdjustLogIds()).hasSize(2);
        assertThat(count("ip_pool_status_stock")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT audit_status FROM ip_adjust_log_stock",String.class)).containsOnly("20");
        assertThat(jdbc.queryForList("SELECT DISTINCT adjust_log_id FROM ip_adjust_step_stock",Long.class)).hasSize(1);
        sql("UPDATE ip_pool_relation SET relation_type='out_linked'", "UPDATE ip_adjust_log_stock SET submit_time='2026-10-01'");
        StockPoolAdjustSubmitReq outbound = request("600001.SH",true,1L);
        outbound.getItems().get(0).setAdjustMode("调出"); outbound.getItems().get(0).setFlowType("fastOutbound");
        outbound.getItems().add(item(2L,"linkage",true,"调出"));
        apply.addAdjustLog(outbound);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_stock WHERE is_deleted=0",Integer.class)).isZero();
    }

    /** 修改步骤才能更改附件，原业务同编号附件不得删；终止后不落池。 */
    @Test public void rejectModifyShouldResubmitOrWithdrawWithAttachmentIsolation() {
        // 重提时沿用跨环节人员排除规则，由尚未参与的第二位审批人处理。
        addHandler(106L, 103L, 6L);
        StockAdjustSubmitDto submitted = apply.addAdjustLog(request("600001.SH",false,1L));
        audit.submitAdjustAudit(auditReq(submitted,"5","reject"));
        assertThat(status(submitted)).isEqualTo("11");
        Long logId=submitted.getAdjustLogIds().get(0);
        attachment(9L,"ip_adjust_log",logId,"credit_report_hand");
        StockPoolAdjustAuditReq req=auditReq(submitted,"1","approve"); req.setAdjustReason("已修订原因");
        StockPoolAdjustAuditReq.AttachmentChange change=new StockPoolAdjustAuditReq.AttachmentChange();
        change.setAdjustLogId(logId); change.setDeleteAttachmentIds(Collections.singletonList(9L));
        req.setAttachmentChanges(Collections.singletonList(change));
        assertThatThrownBy(() -> audit.submitAdjustAudit(req)).isInstanceOf(BizException.class).hasMessageContaining("当前业务");
        assertThat(jdbc.queryForObject("SELECT adjust_reason FROM ip_adjust_log_stock WHERE id=?",String.class,logId)).isNull();
        assertThat(status(submitted)).isEqualTo("11");
        req.setAttachmentChanges(Collections.emptyList()); audit.submitAdjustAudit(req);
        assertThat(status(submitted)).isEqualTo("00");
        audit.submitAdjustAudit(auditReq(submitted,"6","reject"));
        audit.submitAdjustAudit(auditReq(submitted,"1","reject"));
        assertThat(status(submitted)).isEqualTo("99"); assertThat(count("ip_pool_status_stock")).isZero();
    }

    /** 最近两次评级按日期和主键倒序，已删除记录不算；分管和自选是交集且多池不重复。 */
    @Test public void ratingIntersectionNaturalDaysAndMultiPoolPagingShouldBeCorrect() {
        sql("INSERT INTO rrs_stock_rating(stock_code,rating_code,rating_date,is_deleted) VALUES('600001.SH','sell','2026-01-01',0),('600001.SH','buy','2026-10-01',0),('600001.SH','overweight','2026-10-01',0),('600001.SH','neutral','2026-10-02',1)",
                "INSERT INTO rrs_stock_manager(stock_code,user_id,is_deleted) VALUES('600001.SH','2',0),('600001.SH','2',0),('600001.SH','3',0),('600002.SH','2',0)",
                "INSERT INTO ip_pool_status_stock(stock_code,target_pool_id,audit_status,is_deleted,entry_time) VALUES('600001.SH',1,'20',0,'2026-10-08 23:59:59'),('600001.SH',2,'20',0,'2026-10-08 23:59:59.999'),('600002.SH',1,'20',0,'2026-10-09'),('600003.SH',1,'00',0,'2026-10-08')");
        MyStockPoolReq favorite=favorite("600001.SH","2"); queries.addStockToMyPool(favorite);
        StockPoolQueryReq req=query(); req.setMyManagedStocks(true); req.setMyStocks(true); req.setEntryTimeEnd("2026-10-08"); req.setPageSize(1);
        PageResult<StockPoolQueryDto> first=queries.queryStockPoolPage(req);
        assertThat(first.getTotal()).isEqualTo(2);
        StockPoolQueryDto row=first.getRecords().get(0);
        assertThat(row.getLatestRating()).isEqualTo("overweight"); assertThat(row.getPreviousRating()).isEqualTo("buy");
        req.setPageIndex(2); assertThat(queries.queryStockPoolPage(req).getRecords().get(0).getTargetPoolId()).isNotEqualTo(row.getTargetPoolId());
        req.setCurrentUserId("3"); assertThat(queries.queryStockPoolPage(req).getTotal()).isZero();
        req.setMyStocks(false); assertThat(queries.queryStockPoolPage(req).getTotal()).isEqualTo(2);
        req.setEntryTimeStart("2026-10-09"); assertThatThrownBy(() -> queries.queryStockPoolPage(req)).isInstanceOf(BizException.class);
    }

    /** 收藏并发幂等、按个人隔离，并且基础数据决定股票品种和市场。 */
    @Test public void favoritesShouldBeConcurrentIdempotentAndUserIsolated() throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(4);
        try {
            List<Future<Long>> results=new ArrayList<>();
            for(int i=0;i<8;i++) results.add(workers.submit(() -> queries.addStockToMyPool(favorite("600001.SH","2")).getId()));
            Set<Long> ids=new HashSet<>(); for(Future<Long> result:results) ids.add(result.get(15,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1); assertThat(count("my_security_pool")).isEqualTo(1);
            queries.addStockToMyPool(favorite("600001.SH","3"));
            queries.deleteStockFromMyPool(favorite("600001.SH","2"));
            queries.deleteStockFromMyPool(favorite("600001.SH","2"));
            assertThat(queries.queryFavoritedCodeList(favorite(null,"2"))).isEmpty();
            assertThat(queries.queryFavoritedCodeList(favorite(null,"3"))).containsExactly("600001.SH");
        } finally { workers.shutdownNow(); }
    }

    /** 非空评级准入在提交和终审检查，未知编码配置不能默认放行。 */
    @Test public void gradeAdmissionShouldRejectUnratedUnknownAndChangedRatings() {
        sql("UPDATE ip_investment_pool SET grade_astrict='buy,overweight' WHERE id=1");
        assertThatThrownBy(() -> apply.addAdjustLog(request("600001.SH",false,1L))).isInstanceOf(BizException.class).hasMessageContaining("评级");
        sql("INSERT INTO rrs_stock_rating(stock_code,rating_code,rating_date,is_deleted) VALUES('600001.SH','buy','2026-10-01',0)");
        StockAdjustSubmitDto submitted=apply.addAdjustLog(request("600001.SH",false,1L));
        sql("INSERT INTO rrs_stock_rating(stock_code,rating_code,rating_date,is_deleted) VALUES('600001.SH','sell','2026-10-08',0)");
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(submitted,"5","approve"))).isInstanceOf(BizException.class).hasMessageContaining("评级");
        assertThat(status(submitted)).isEqualTo("00"); assertThat(count("ip_pool_status_stock")).isZero();
        sql("UPDATE ip_investment_pool SET grade_astrict='AAA' WHERE id=1");
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(submitted,"5","approve"))).isInstanceOf(BizException.class).hasMessageContaining("配置不合法");
    }

    /** 不同股票同时争抢最后一个名额，池锁确保只落一个，失败审核事务回滚。 */
    @Test public void finalCapacityLockShouldPreventConcurrentOverflow() throws Exception {
        sql("UPDATE ip_investment_pool SET max_capacity=1 WHERE id=1");
        StockAdjustSubmitDto a=apply.addAdjustLog(request("600001.SH",false,1L));
        StockAdjustSubmitDto b=apply.addAdjustLog(request("600002.SH",false,1L));
        StockPoolAdjustAuditReq ar=auditReq(a,"5","approve"),br=auditReq(b,"5","approve");
        ExecutorService workers=Executors.newFixedThreadPool(2); CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(StockPoolAdjustAuditReq req:Arrays.asList(ar,br)) results.add(workers.submit(() -> {
                ready.countDown(); start.await();
                try { audit.submitAdjustAudit(req); return true; }
                catch(BizException ex) { assertThat(ex.getMessage()).contains("容量已满"); return false; }
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            int completed=0; for(Future<Boolean> result:results) if(result.get(15,TimeUnit.SECONDS)) completed++;
            assertThat(completed).isEqualTo(1); assertThat(count("ip_pool_status_stock")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_step_stock WHERE step_status='pending'",Integer.class)).isEqualTo(1);
        } finally { workers.shutdownNow(); }
    }

    /** 会签要等待全部处理人；未授权人员、伪造步骤上下文和发起人不能处理普通节点。 */
    @Test public void countersignAndAuditPermissionsShouldBeEnforced() {
        configureFlow(10L,false,"all"); addHandler(106L,103L,6L);
        StockAdjustSubmitDto submitted=apply.addAdjustLog(request("600001.SH",false,1L));
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(submitted,"7","approve"))).isInstanceOf(BizException.class).hasMessageContaining("不是");
        StockPoolAdjustAuditReq forged=auditReq(submitted,"5","approve"); forged.setAdjustBatchNo("OTHER");
        assertThatThrownBy(() -> audit.submitAdjustAudit(forged)).isInstanceOf(BizException.class).hasMessageContaining("上下文");
        audit.submitAdjustAudit(auditReq(submitted,"5","approve"));
        assertThat(status(submitted)).isEqualTo("00"); assertThat(count("ip_pool_status_stock")).isZero();
        audit.submitAdjustAudit(auditReq(submitted,"6","approve"));
        assertThat(status(submitted)).isEqualTo("20");
    }

    /** 多来源池采用 OR；评级、市场、股票状态及配置错误必须阻断。 */
    @Test public void sourceOrAndInvalidDataShouldFollowBondRules() {
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'source',2,0),(1,'source',3,0)",
                "INSERT INTO ip_pool_status_stock(stock_code,target_pool_id,audit_status,is_deleted) VALUES('600001.SH',2,'20',0)");
        apply.addAdjustLog(request("600001.SH",true,1L)); assertThat(count("ip_pool_status_stock")).isEqualTo(2);
        sql("UPDATE rrs_stockinfo SET security_status='D' WHERE stock_code='600002.SH'");
        assertThatThrownBy(() -> apply.addAdjustLog(request("600002.SH",true,1L))).isInstanceOf(BizException.class).hasMessageContaining("退市");
        sql("UPDATE ip_investment_pool SET simple_in_flow_key='invalid' WHERE id=3");
        assertThatThrownBy(() -> apply.addAdjustLog(request("600003.SH",true,3L))).isInstanceOf(BizException.class).hasMessageContaining("配置");
    }

    /** 市场、行业、开放日及冻结期配置与债券口径一致，指数模式非零时跳过行业限制。 */
    @Test public void admissionOpenDaysAndFreezeShouldRejectInvalidAdjustments() {
        sql("UPDATE ip_investment_pool SET market_codes='[\"HKEX\"]' WHERE id=1");
        assertThatThrownBy(() -> apply.addAdjustLog(request("600001.SH",true,1L)))
                .isInstanceOf(BizException.class).hasMessageContaining("市场");
        sql("UPDATE ip_investment_pool SET market_codes='[\"SSE\"]',industry_code='渔业' WHERE id=1");
        assertThatThrownBy(() -> apply.addAdjustLog(request("600001.SH",true,1L)))
                .isInstanceOf(BizException.class).hasMessageContaining("行业");
        sql("UPDATE ip_investment_pool SET industry_exponent=2,open_day_adjust=1 WHERE id=1");
        assertThatThrownBy(() -> apply.addAdjustLog(request("600001.SH",true,1L)))
                .isInstanceOf(BizException.class).hasMessageContaining("开放日");
        sql("INSERT INTO ip_pool_open_day(pool_id,begin_date,end_date,is_deleted) VALUES(1,'2000-01-01','2099-12-31',0)");
        apply.addAdjustLog(request("600001.SH",true,1L));
        sql("UPDATE ip_investment_pool SET frozen_period_in=100000 WHERE id=1",
                "UPDATE ip_adjust_log_stock SET submit_time='2000-01-01'");
        StockPoolAdjustSubmitReq outbound=request("600001.SH",true,1L);
        outbound.getItems().get(0).setAdjustMode("调出");
        outbound.getItems().get(0).setFlowType("fastOutbound");
        assertThatThrownBy(() -> apply.addAdjustLog(outbound)).isInstanceOf(BizException.class).hasMessageContaining("冻结期");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_stock WHERE is_deleted=0",Integer.class)).isEqualTo(1);
        sql("UPDATE ip_investment_pool SET frozen_period_in=0 WHERE id=1");
        apply.addAdjustLog(outbound);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_stock WHERE is_deleted=0",Integer.class)).isZero();
    }

    /** 互斥调出与调入整组生效；其他批次待审来源池不能作为已生效来源。 */
    @Test public void mutexGroupAndPendingSourceIsolationShouldBeEnforced() {
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'in_mutex',2,0),(1,'source',2,0)",
                "INSERT INTO ip_pool_status_stock(stock_code,target_pool_id,audit_status,is_deleted,entry_time) VALUES('600001.SH',2,'20',0,CURRENT_TIMESTAMP)");
        StockPoolAdjustSubmitReq req=request("600001.SH",true,1L);
        StockPoolAdjustSubmitReq.AdjustItem mutex=item(2L,"mutex",true,"调出");
        mutex.setFlowType("fastOutbound");
        req.getItems().add(mutex);
        apply.addAdjustLog(req);
        assertThat(jdbc.queryForList("SELECT target_pool_id FROM ip_pool_status_stock WHERE is_deleted=0",Long.class)).containsExactly(1L);
        assertThat(jdbc.queryForList("SELECT audit_status FROM ip_adjust_log_stock",String.class)).containsExactly("20","20");
        apply.addAdjustLog(request("600002.SH",false,2L));
        assertThatThrownBy(() -> apply.addAdjustLog(request("600002.SH",true,1L)))
                .isInstanceOf(BizException.class).hasMessageContaining("来源池");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_stock WHERE stock_code='600002.SH'",Integer.class)).isEqualTo(1);
    }

    /** 无效快速配置和重复审核路由显式拒绝，终审失败保留原 pending 和未生效状态。 */
    @Test public void invalidFastFlowAndAmbiguousAuditRouteShouldRollBack() {
        sql("UPDATE ip_investment_pool SET simple_in_flow_id=NULL WHERE id=1");
        assertThatThrownBy(() -> apply.addAdjustLog(request("600001.SH",true,1L)))
                .isInstanceOf(BizException.class).hasMessageContaining("流程");
        assertThat(count("ip_adjust_log_stock")).isZero();
        StockAdjustSubmitDto submitted=apply.addAdjustLog(request("600001.SH",false,1L));
        List<FlowEdgeBo> duplicate=new ArrayList<>(flows.queryFlowEdgeListByVersionId(10L));
        duplicate.add(edge(103L,105L,"approve"));
        when(flows.queryFlowEdgeListByVersionId(10L)).thenReturn(duplicate);
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(submitted,"5","approve")))
                .isInstanceOf(BizException.class).hasMessageContaining("路由不唯一");
        assertThat(status(submitted)).isEqualTo("00");
        assertThat(count("ip_pool_status_stock")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_step_stock WHERE step_status='pending'",Integer.class)).isEqualTo(1);
    }

    /** 报告选择和终审需要真实股票来源；相同日志 ID 的债券附件不能满足股票报告。 */
    @Test public void reportsShouldValidateSourceAndAttachmentBusiness() {
        sql("INSERT INTO rrs_report_in(id,security_code,security_type,is_deleted) VALUES(7,'600001.SH','stock',0),(8,'600002.SH','stock',0)");
        attachment(7L,"rrs_report_in",7L,"report_in"); attachment(8L,"rrs_report_in",8L,"report_in");
        attachments.validateStockReportSources(Collections.singletonList(7L),"600001.SH",true);
        assertThatThrownBy(() -> attachments.validateStockReportSources(Collections.singletonList(8L),"600001.SH",false))
                .isInstanceOf(BizException.class).hasMessageContaining("不匹配");
        sql("UPDATE ip_investment_pool SET in_report_restriction='any' WHERE id=1");
        StockPoolAdjustSubmitReq req=request("600001.SH",false,1L); req.getItems().get(0).setReportSourceAttachmentIds(Collections.singletonList(7L));
        StockAdjustSubmitDto submitted=apply.addAdjustLog(req);
        sql("UPDATE rrs_report_in SET is_deleted=1 WHERE id=7");
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(submitted,"5","approve"))).isInstanceOf(BizException.class).hasMessageContaining("来源已失效");
        assertThat(status(submitted)).isEqualTo("00");
    }

    /** Excel 包含全部查询命中记录与指定列，日期截止包含整天。 */
    @Test public void exportShouldKeepAllFiltersAndExactColumns() throws Exception {
        sql("INSERT INTO ip_pool_status_stock(stock_code,target_pool_id,audit_status,is_deleted,entry_time) VALUES('600001.SH',1,'20',0,'2026-10-08 23:59:59'),('600001.SH',2,'20',0,'2026-10-08 23:59:59')");
        StockPoolQueryReq req=query(); req.setPageSize(1); req.setEntryTimeEnd("2026-10-08");
        CommonFileDto file=queries.exportStockPoolExcel(req);
        try(XSSFWorkbook workbook=workbook(file)) {
            assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)8);
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(2).getStringCellValue()).isEqualTo("所属行业");
        }
        StockAdjustSubmitDto submitted=apply.addAdjustLog(request("600002.SH",false,3L));
        StockPoolAdjustHistoryReq historyReq=new StockPoolAdjustHistoryReq(); historyReq.setAuditStatus("00"); historyReq.setIndustryCode("A02");
        historyReq.setPageSize(1);
        try(XSSFWorkbook workbook=workbook(history.exportStockPoolAdjustHistoryExcel(historyReq))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)9);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(8).getStringCellValue()).isEqualTo("流程中");
        }
        assertThat(history.queryIndustryList()).extracting("industryCode").containsExactly("A02");
        assertThat(submitted.getAdjustBatchNos().get(0)).startsWith("STOCK");
    }

    /** 设置发布的一般/快速流程和对应节点、连线、用户。 */
    private void configureFlow(Long id,boolean fast,String strategy) {
        FlowDefinitionBo definition=new FlowDefinitionBo(); definition.setId(id); definition.setFlowKey(fast?"stock_fast":"stock_normal"); definition.setName("股票流程"); definition.setStatus("active");
        FlowVersionBo version=new FlowVersionBo(); version.setId(id); version.setStatus("active");
        List<FlowNodeBo> nodes=new ArrayList<>(); List<FlowEdgeBo> edges=new ArrayList<>(); List<NodeApprovalConfigBo> configs=new ArrayList<>();
        long base=id*10; nodes.add(node(base+1,id,"start","开始")); nodes.add(node(base+2,id,"approval","发起")); nodes.add(node(base+5,id,"end","结束"));
        configs.add(config(base+2,"initiator")); edges.add(edge(base+1,base+2,"auto"));
        if(fast) { edges.add(edge(base+2,base+5,"submit")); }
        else {
            nodes.add(node(base+3,id,"approval","审核")); nodes.add(node(base+4,id,"approval","修改"));
            configs.add(config(base+3,strategy)); configs.add(config(base+4,"initiator"));
            edges.add(edge(base+2,base+3,"submit")); edges.add(edge(base+3,base+5,"approve")); edges.add(edge(base+3,base+4,"reject"));
            edges.add(edge(base+4,base+3,"resubmit")); edges.add(edge(base+4,base+5,"reject"));
            handlers.removeIf(h -> h.getApprovalConfigId().equals(base+3)); addHandler(base+3,base+3,5L);
        }
        when(flows.queryFlowById(id)).thenReturn(definition); when(flows.queryFlowVersionByFlowIdList(id,null)).thenReturn(Collections.singletonList(version));
        when(flows.queryFlowNodeListByVersionId(id)).thenReturn(nodes); when(flows.queryFlowEdgeListByVersionId(id)).thenReturn(edges);
        when(flows.queryApprovalConfigListByVersionId(id)).thenReturn(configs);
        when(flows.queryApprovalHandlerListByVersionId(id)).thenAnswer(call -> new ArrayList<>(handlers));
        for(FlowNodeBo node:nodes) when(flows.queryFlowNodeById(node.getId())).thenReturn(node);
    }

    /** 创建流程节点。 */
    private FlowNodeBo node(long id,long version,String type,String label) { FlowNodeBo n=new FlowNodeBo();n.setId(id);n.setVersionId(version);n.setNodeId("n"+id);n.setNodeType(type);n.setLabel(label);n.setSortOrder((int)(id%10));return n; }
    /** 创建动作路由连线。 */
    private FlowEdgeBo edge(long from,long to,String action) { FlowEdgeBo e=new FlowEdgeBo();e.setFromNodeId(from);e.setToNodeId(to);e.setRouteAction(action);return e; }
    /** 创建审批配置。 */
    private NodeApprovalConfigBo config(long node,String strategy) { NodeApprovalConfigBo c=new NodeApprovalConfigBo();c.setId(node);c.setNodeId(node);c.setApprovalStrategy(strategy);return c; }
    /** 添加用户审批人。 */
    private void addHandler(long id,long config,long user) { NodeApprovalHandlerBo h=new NodeApprovalHandlerBo();h.setId(id);h.setApprovalConfigId(config);h.setHandlerType("user");h.setHandlerId(user);h.setHandlerName("审批人"+user);handlers.add(h); }
    /** 在内存数据库准备有效股票基础主行。 */
    private void stock(String code) { jdbc.update("INSERT INTO rrs_stockinfo(stock_code,stock_name,stock_short_name,industry_code,industry_name,security_type,security_status,market_code,is_deleted,previous_close_price) VALUES(?,?,?,?,?,?,?,?,?,?)",code,"股票"+code,"股票简称","A02","林业","stock_a","L","SSE",0,0); }
    /** 创建手工申请。 */
    private StockPoolAdjustSubmitReq request(String code,boolean fast,Long pool) { StockPoolAdjustSubmitReq q=new StockPoolAdjustSubmitReq();q.setStockCode(code);q.setAdjusterId("1");q.setAdjusterName("管理员");q.setItems(new ArrayList<>(Collections.singletonList(item(pool,"manual",fast,"调入"))));return q; }
    /** 创建同组调整项。 */
    private StockPoolAdjustSubmitReq.AdjustItem item(Long pool,String tag,boolean fast,String direction) { StockPoolAdjustSubmitReq.AdjustItem i=new StockPoolAdjustSubmitReq.AdjustItem();i.setTargetPoolId(pool);i.setItemTag(tag);i.setAdjustMode(direction);i.setAdjustGroupKey("stock-group-1");i.setFlowId(fast?20L:10L);i.setFlowKey(fast?"stock_fast":"stock_normal");i.setFlowType((fast?"fast":"normal")+(direction.equals("调入")?"Inbound":"Outbound"));return i; }
    /** 从真实待办取得审核请求，未授权用户使用现存待办 ID 验证权限。 */
    private StockPoolAdjustAuditReq auditReq(StockAdjustSubmitDto submitted,String user,String action) { List<StockAdjustStepBo> steps=adjustments.queryAdjustStepByBatchList(submitted.getAdjustLogIds().get(0),submitted.getAdjustBatchNos().get(0));StockAdjustStepBo s=steps.stream().filter(row -> "pending".equals(row.getStepStatus()) && user.equals(row.getHandlerId())).findFirst().orElseGet(() -> steps.stream().filter(row -> "pending".equals(row.getStepStatus())).findFirst().get());StockPoolAdjustAuditReq req=new StockPoolAdjustAuditReq();req.setStepId(s.getId());req.setAdjustLogId(s.getAdjustLogId());req.setAdjustBatchNo(s.getAdjustBatchNo());req.setHandlerId(user);req.setHandlerName("用户"+user);req.setProcessAction(action);req.setProcessComment("处理意见");return req; }
    /** 构造查询当前用户。 */
    private StockPoolQueryReq query() { StockPoolQueryReq q=new StockPoolQueryReq();q.setCurrentUserId("2");return q; }
    /** 构造个人收藏请求。 */
    private MyStockPoolReq favorite(String code,String user) { MyStockPoolReq q=new MyStockPoolReq();q.setStockCode(code);q.setCurrentUserId(user);return q; }
    /** 插入业务限定的附件元数据，不创建物理文件。 */
    private void attachment(Long id,String table,Long main,String category) { jdbc.update("INSERT INTO sys_attachment(id,table_name,main_id,attachment_category,file_name,original_file_name,is_deleted) VALUES(?,?,?,?,?,?,0)",id,table,main,category,"report/"+id+".pdf","股票报告.pdf"); }
    /** 当前批次状态。 */
    private String status(StockAdjustSubmitDto q) { return jdbc.queryForObject("SELECT audit_status FROM ip_adjust_log_stock WHERE id=?",String.class,q.getAdjustLogIds().get(0)); }
    /** 查询固定测试表行数。 */
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    /** 在 H2 执行固定测试数据准备。 */
    private void sql(String... commands) { for(String command:commands) jdbc.execute(command); }
    /** 打开服务实际导出的 Excel。 */
    private XSSFWorkbook workbook(CommonFileDto file) throws Exception { return new XSSFWorkbook(new ByteArrayInputStream(Base64.getDecoder().decode(file.getContentBase64()))); }
    /** 使用服务注解创建真正事务代理。 */
    @SuppressWarnings("unchecked") private <T> T transactional(T target) { ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (T)proxy.getProxy(); }
}
