package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.*;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustDto;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustReq;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.AdditionalAnswers;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 Mapper、内存事务和主行锁验证股票申请、审批、查询及附件完整链路。 */
public class StockPoolIntegrationTest {
    /** 每个测试独立的附件存储目录，结束后由 JUnit 清理。 */
    @Rule public TemporaryFolder uploadDirectory = new TemporaryFolder();
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
    /** 股票批量编排服务，运行表及事务沿用真实单笔链路。 */
    private BatchStockPoolAdjustService batches;
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
        ReflectionTestUtils.setField(attachments,"storagePath",uploadDirectory.getRoot().getAbsolutePath());
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
        batches = new BatchStockPoolAdjustService();
        BatchStockPoolAdjustMapper batchMapper = mock(BatchStockPoolAdjustMapper.class);
        when(batchMapper.queryEnabledStockLeafPoolCount(1L)).thenReturn(1);
        ReflectionTestUtils.setField(batches, "batchStockPoolAdjustMapper", batchMapper);
        ReflectionTestUtils.setField(batches, "investmentPoolMapper", pools);
        ReflectionTestUtils.setField(batches, "investmentPoolService", paths);
        ReflectionTestUtils.setField(batches, "stockPoolAdjustService", apply);
        ReflectionTestUtils.setField(batches, "sysAttachmentService", attachments);
        batches = transactional(batches);
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

    /** 两只股票各自完成一般流程，联动与反向互斥整组进入待办、当前池、历史及详情。 */
    @Test public void batchNormalFlowShouldReachWholeGroupsMattersQueryHistoryAndDetails() {
        // 为两只股票建立调入联动和已在池的反向互斥项。
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'in_linked',3,0),(1,'in_mutex',2,0)",
                "INSERT INTO ip_pool_status_stock(stock_code,target_pool_id,audit_status,is_deleted,entry_time) VALUES('600001.SH',2,'20',0,CURRENT_TIMESTAMP),('600002.SH',2,'20',0,CURRENT_TIMESTAMP)",
                "INSERT INTO rrs_stock_rating(stock_code,rating_code,rating_date,is_deleted) VALUES('600001.SH','buy','2026-10-01',0),('600002.SH','overweight','2026-10-01',0)");
        // 使用真实批量校验结果构造完整主项及关系项申请。
        BatchStockAdjustDto submitted = batches.addAdjustLog(batchRequest("600001.SH", "600002.SH"));
        assertThat(submitted.getStockCount()).isEqualTo(2);
        assertThat(submitted.getSubmitCount()).isEqualTo(6);
        assertThat(submitted.getAdjustBatchNos()).hasSize(2).doesNotHaveDuplicates();
        assertThat(submitted.getAdjustBatchNos()).allMatch(batch -> batch.startsWith("STOCK"));
        assertThat(jdbc.queryForList("SELECT adjust_type FROM ip_adjust_log_stock WHERE target_pool_id=1", String.class))
                .containsOnly("手动批量调整");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_stock WHERE adjust_mode='调出' AND target_pool_id=2", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT audit_status FROM ip_adjust_log_stock", String.class)).containsOnly("00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_stock WHERE is_deleted=0", Integer.class)).isEqualTo(2);
        MyMattersReq matter = new MyMattersReq(); matter.setBusinessDomain("stock"); matter.setCurrentUserId("5"); matter.setStepStatus("pending");
        assertThat(matters.queryMyMattersPage(matter).getRecords()).extracting("stockCode").containsExactlyInAnyOrder("600001.SH", "600002.SH");
        for (String code : Arrays.asList("600001.SH", "600002.SH")) {
            // 从真实批次待办完成本股票整组审批。
            StockAdjustSubmitDto group = batchGroup(code);
            audit.submitAdjustAudit(auditReq(group, "5", "approve"));
            StockPoolAdjustReq detail = new StockPoolAdjustReq(); detail.setStockCode(code); detail.setAdjustBatchNo(group.getAdjustBatchNos().get(0));
            assertThat(apply.queryAdjustLogList(detail)).hasSize(3).extracting("stockCode").containsOnly(code);
        }
        assertThat(jdbc.queryForList("SELECT audit_status FROM ip_adjust_log_stock", String.class)).containsOnly("20");
        assertThat(jdbc.queryForList("SELECT target_pool_id FROM ip_pool_status_stock WHERE is_deleted=0", Long.class))
                .containsExactlyInAnyOrder(1L, 3L, 1L, 3L);
        assertThat(queries.queryStockPoolPage(query()).getTotal()).isEqualTo(4);
        assertThat(history.queryStockPoolAdjustHistoryPage(new StockPoolAdjustHistoryReq()).getTotal()).isEqualTo(6);
        assertThat(matters.queryMyMattersPage(matter).getRecords()).isEmpty();
        assertThat(jdbc.queryForList("SELECT DISTINCT adjust_log_id FROM ip_adjust_step_stock", Long.class)).hasSize(2);
    }

    /** 同一份中文文件跨股票、关系项及附件分类绑定，物理存储只发生一次。 */
    @Test public void batchSharedChineseFileShouldBeStoredOnceAndBoundToEveryLog() throws Exception {
        // 生成包含关系项的两只股票申请并共享同一文件下标。
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'in_linked',2,0)");
        BatchStockAdjustReq request = batchRequest("600001.SH", "600002.SH");
        for (BatchStockAdjustReq.AdjustItem row : request.getItems()) {
            row.setReportFileIndexes(Collections.singletonList(0));
            row.setMaterialFileIndexes(Collections.singletonList(0));
        }
        MockMultipartFile file = new MockMultipartFile("files", "multipart-name.pdf", "application/pdf", "共享股票材料".getBytes(StandardCharsets.UTF_8));
        BatchStockAdjustDto submitted = batches.addAdjustLog(request, Collections.singletonList(file), "[\"股票批量研究报告.pdf\"]");
        assertThat(submitted.getSubmitCount()).isEqualTo(4);
        assertThat(count("sys_attachment")).isEqualTo(8);
        assertThat(jdbc.queryForList("SELECT DISTINCT table_name FROM sys_attachment", String.class)).containsExactly("ip_adjust_log_stock");
        assertThat(jdbc.queryForList("SELECT DISTINCT original_file_name FROM sys_attachment", String.class)).containsExactly("股票批量研究报告.pdf");
        assertThat(jdbc.queryForList("SELECT DISTINCT file_name FROM sys_attachment", String.class)).hasSize(1);
        assertThat(jdbc.queryForList("SELECT DISTINCT attachment_category FROM sys_attachment", String.class))
                .containsExactlyInAnyOrder("stock_report_hand", "stock_material_hand");
        assertThat(jdbc.queryForList("SELECT DISTINCT main_id FROM sys_attachment", Long.class)).containsExactlyInAnyOrderElementsOf(submitted.getLogIds());
        // 检查真实文件内容，防止仅附件元数据去重而重复保存物理文件。
        List<Path> stored = storedFiles();
        assertThat(stored).hasSize(1);
        assertThat(Files.readAllBytes(stored.get(0))).isEqualTo(file.getBytes());
    }

    /** 第二只股票锁后失效时，全批完成复核前不写任何日志、步骤、附件或文件。 */
    @Test public void batchSecondStockPreparationFailureShouldPerformZeroWrites() throws Exception {
        // 先取得两只有效股票的完整申请，再模拟第二只股票退市。
        BatchStockAdjustReq request = batchRequest("600001.SH", "600002.SH");
        for (BatchStockAdjustReq.AdjustItem row : request.getItems()) { row.setReportFileIndexes(Collections.singletonList(0)); }
        sql("UPDATE rrs_stockinfo SET security_status='D' WHERE stock_code='600002.SH'");
        // 观察真实 Mapper 写入调用，区分预检零写入与写后事务回滚。
        StockPoolAdjustMapper observed = observeAdjustments();
        MockMultipartFile file = new MockMultipartFile("files", "report.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> batches.addAdjustLog(request, Collections.singletonList(file), "[\"股票报告.pdf\"]"))
                .isInstanceOf(BizException.class).hasMessageContaining("退市");
        verify(observed, never()).addAdjustLog(any(StockAdjustLogBo.class));
        assertThat(count("ip_adjust_log_stock")).isZero();
        assertThat(count("ip_adjust_step_stock")).isZero();
        assertThat(count("sys_attachment")).isZero();
        // 预检失败没有创建共享文件。
        assertThat(storedFiles()).isEmpty();
    }

    /** 第二只股票写入失败时，已保存的第一只股票及共享物理文件也必须回滚。 */
    @Test public void batchLaterStockWriteFailureShouldRollbackAllRecordsAndSharedFile() throws Exception {
        // 两只股票共享报告，第一只完成日志、附件及初始步骤保存。
        BatchStockAdjustReq request = batchRequest("600001.SH", "600002.SH");
        for (BatchStockAdjustReq.AdjustItem row : request.getItems()) { row.setReportFileIndexes(Collections.singletonList(0)); }
        // 委托真实 Mapper，仅在第二只股票写入时模拟持久化错误。
        StockPoolAdjustMapper observed = observeAdjustments();
        boolean[] firstStockWasStored = {false};
        doAnswer(call -> {
            StockAdjustLogBo log = call.getArgument(0);
            if ("600002.SH".equals(log.getStockCode())) {
                assertThat(count("ip_adjust_log_stock")).isEqualTo(1);
                assertThat(count("ip_adjust_step_stock")).isGreaterThan(0);
                assertThat(count("sys_attachment")).isEqualTo(1);
                // 在故障发生前确认共享文件已经真实保存。
                assertThat(storedFiles()).hasSize(1);
                firstStockWasStored[0] = true;
                throw new BizException("第二只股票写入失败");
            }
            return adjustments.addAdjustLog(log);
        }).when(observed).addAdjustLog(any(StockAdjustLogBo.class));
        MockMultipartFile file = new MockMultipartFile("files", "report.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> batches.addAdjustLog(request, Collections.singletonList(file), "[\"共享股票报告.pdf\"]"))
                .isInstanceOf(BizException.class).hasMessageContaining("第二只股票写入失败");
        assertThat(firstStockWasStored[0]).isTrue();
        assertThat(count("ip_adjust_log_stock")).isZero();
        assertThat(count("ip_adjust_step_stock")).isZero();
        assertThat(count("sys_attachment")).isZero();
        assertThat(count("ip_pool_status_stock")).isZero();
        // 外层真实事务回滚清理第一只股票创建的共享物理文件。
        assertThat(storedFiles()).isEmpty();
    }

    /** 后续股票的无效其他材料引用及本地索引均须在全批日志写入前拒绝。 */
    @Test public void batchInvalidMaterialSourceAndFileIndexShouldPerformZeroWrites() throws Exception {
        // 观察真实写入，确保前一股票不会先创建日志。
        StockPoolAdjustMapper observed = observeAdjustments();
        BatchStockAdjustReq request = batchRequest("600001.SH", "600002.SH");
        request.getItems().get(1).setMaterialSourceAttachmentIds(Collections.singletonList(9999L));
        assertThatThrownBy(() -> batches.addAdjustLog(request)).isInstanceOf(BizException.class).hasMessageContaining("无效");
        request.getItems().get(1).setMaterialSourceAttachmentIds(Collections.emptyList());
        request.getItems().get(0).setReportFileIndexes(Collections.singletonList(0));
        request.getItems().get(1).setReportFileIndexes(Collections.singletonList(1));
        MockMultipartFile file = new MockMultipartFile("files", "report.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> batches.addAdjustLog(request, Collections.singletonList(file), "[\"股票报告.pdf\"]"))
                .isInstanceOf(BizException.class).hasMessageContaining("下标");
        verify(observed, never()).addAdjustLog(any(StockAdjustLogBo.class));
        assertThat(count("ip_adjust_log_stock")).isZero();
        assertThat(count("ip_adjust_step_stock")).isZero();
        assertThat(count("sys_attachment")).isZero();
        // 无效引用和索引的预检均没有生成物理文件。
        assertThat(storedFiles()).isEmpty();
    }

    /** 合法来源和文件也必须遵守同股票整组及整批共享约束，不能默默补齐差异。 */
    @Test public void batchInconsistentGroupReportsAndSharedMaterialsShouldPerformZeroWrites() throws Exception {
        // 准备同一股票的两个合法内部来源，确保拒绝原因来自共享一致性。
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'in_linked',2,0)",
                "INSERT INTO rrs_report_in(id,security_code,security_type,is_deleted) VALUES(7,'600001.SH','stock',0),(8,'600001.SH','stock',0)");
        attachment(7L, "rrs_report_in", 7L, "report_in"); attachment(8L, "rrs_report_in", 8L, "report_in");
        // 观察真实写入调用，所有不一致请求均不得创建任何股票日志。
        StockPoolAdjustMapper observed = observeAdjustments();
        BatchStockAdjustReq groupRequest = batchRequest("600001.SH");
        groupRequest.getItems().get(0).setReportSourceAttachmentIds(Collections.singletonList(7L));
        groupRequest.getItems().get(1).setReportSourceAttachmentIds(Collections.singletonList(8L));
        assertThatThrownBy(() -> batches.addAdjustLog(groupRequest)).isInstanceOf(BizException.class).hasMessageContaining("研究报告引用必须一致");
        // 两只股票分别指定不同合法文件下标，仍不符合整批共享本地材料约束。
        BatchStockAdjustReq fileRequest = batchRequest("600001.SH", "600002.SH");
        for (BatchStockAdjustReq.AdjustItem row : fileRequest.getItems()) {
            row.setReportFileIndexes(Collections.singletonList("600001.SH".equals(row.getStockCode()) ? 0 : 1));
        }
        List<MockMultipartFile> files = Arrays.asList(
                new MockMultipartFile("files", "first.pdf", "application/pdf", new byte[]{1}),
                new MockMultipartFile("files", "second.pdf", "application/pdf", new byte[]{2}));
        assertThatThrownBy(() -> batches.addAdjustLog(fileRequest, new ArrayList<>(files), "[\"第一份报告.pdf\",\"第二份报告.pdf\"]"))
                .isInstanceOf(BizException.class).hasMessageContaining("必须整批共享");
        // 其他材料可来自任意合法报告，但整个批次必须保持相同引用集合。
        BatchStockAdjustReq materialRequest = batchRequest("600001.SH", "600002.SH");
        for (BatchStockAdjustReq.AdjustItem row : materialRequest.getItems()) {
            row.setMaterialSourceAttachmentIds(Collections.singletonList("600001.SH".equals(row.getStockCode()) ? 7L : 8L));
        }
        assertThatThrownBy(() -> batches.addAdjustLog(materialRequest)).isInstanceOf(BizException.class).hasMessageContaining("必须整批共享");
        verify(observed, never()).addAdjustLog(any(StockAdjustLogBo.class));
        assertThat(count("ip_adjust_log_stock")).isZero();
        assertThat(count("ip_adjust_step_stock")).isZero();
        assertThat(count("sys_attachment")).isEqualTo(2);
        // 不一致校验失败没有保存待上传文件。
        assertThat(storedFiles()).isEmpty();
    }

    /** 批量主项不能因渠道名称不同，在必需本地报告删除后继续终审通过。 */
    @Test public void batchDeletedRequiredLocalReportShouldBlockFinalApproval() throws Exception {
        // 两只股票使用同一份本地报告满足目标池 any 要求。
        sql("UPDATE ip_investment_pool SET in_report_restriction='any' WHERE id=1");
        BatchStockAdjustReq request = batchRequest("600001.SH", "600002.SH");
        for (BatchStockAdjustReq.AdjustItem row : request.getItems()) { row.setReportFileIndexes(Collections.singletonList(0)); }
        MockMultipartFile file = new MockMultipartFile("files", "report.pdf", "application/pdf", new byte[]{1});
        batches.addAdjustLog(request, Collections.singletonList(file), "[\"股票报告.pdf\"]");
        // 模拟第二只股票的已绑定报告被逻辑删除。
        StockAdjustSubmitDto group = batchGroup("600002.SH");
        jdbc.update("UPDATE sys_attachment SET is_deleted=1 WHERE table_name='ip_adjust_log_stock' AND main_id=?", group.getAdjustLogIds().get(0));
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(group, "5", "approve")))
                .isInstanceOf(BizException.class).hasMessageContaining("报告不符合");
        assertThat(status(group)).isEqualTo("00");
        assertThat(count("ip_pool_status_stock")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_step_stock WHERE step_status='pending'", Integer.class)).isEqualTo(2);
    }

    /** 内部报告来源失效后，批量股票也需保留原待办及未生效状态。 */
    @Test public void batchDeletedInternalReportSourceShouldBlockFinalApproval() {
        // 每只股票引用自己的内部研究报告，禁止跨股票共享来源。
        sql("UPDATE ip_investment_pool SET in_report_restriction='internal' WHERE id=1",
                "INSERT INTO rrs_report_in(id,security_code,security_type,is_deleted) VALUES(7,'600001.SH','stock',0),(8,'600002.SH','stock',0)");
        attachment(7L, "rrs_report_in", 7L, "report_in"); attachment(8L, "rrs_report_in", 8L, "report_in");
        BatchStockAdjustReq request = batchRequest("600001.SH", "600002.SH");
        for (BatchStockAdjustReq.AdjustItem row : request.getItems()) {
            row.setReportSourceAttachmentIds(Collections.singletonList("600001.SH".equals(row.getStockCode()) ? 7L : 8L));
        }
        batches.addAdjustLog(request);
        sql("UPDATE rrs_report_in SET is_deleted=1 WHERE id=8");
        // 第二只股票终审重新核对来源，不因批量渠道而绕过内部报告校验。
        StockAdjustSubmitDto group = batchGroup("600002.SH");
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(group, "5", "approve")))
                .isInstanceOf(BizException.class).hasMessageContaining("来源已失效");
        assertThat(status(group)).isEqualTo("00");
        assertThat(count("ip_pool_status_stock")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_step_stock WHERE step_status='pending'", Integer.class)).isEqualTo(2);
    }

    /** 驳回修改删除批量主项唯一必需报告时，附件、原因和待办必须一起回滚。 */
    @Test public void batchRejectedModifyShouldRejectDeletingRequiredReport() throws Exception {
        // 本地报告满足 any 要求，审批驳回后由发起人修改。
        sql("UPDATE ip_investment_pool SET in_report_restriction='any' WHERE id=1");
        BatchStockAdjustReq request = batchRequest("600001.SH"); request.getItems().get(0).setReportFileIndexes(Collections.singletonList(0));
        MockMultipartFile file = new MockMultipartFile("files", "report.pdf", "application/pdf", new byte[]{1});
        batches.addAdjustLog(request, Collections.singletonList(file), "[\"股票报告.pdf\"]");
        // 定位本股票的真实修改待办及已绑定附件。
        StockAdjustSubmitDto group = batchGroup("600001.SH");
        audit.submitAdjustAudit(auditReq(group, "5", "reject"));
        Long logId = group.getAdjustLogIds().get(0);
        Long attachmentId = jdbc.queryForObject("SELECT id FROM sys_attachment WHERE table_name='ip_adjust_log_stock' AND main_id=?", Long.class, logId);
        StockPoolAdjustAuditReq modify = auditReq(group, "1", "approve"); modify.setAdjustReason("删除报告后的修订原因");
        StockPoolAdjustAuditReq.AttachmentChange change = new StockPoolAdjustAuditReq.AttachmentChange();
        change.setAdjustLogId(logId); change.setDeleteAttachmentIds(Collections.singletonList(attachmentId));
        modify.setAttachmentChanges(Collections.singletonList(change));
        assertThatThrownBy(() -> audit.submitAdjustAudit(modify)).isInstanceOf(BizException.class).hasMessageContaining("报告不符合");
        assertThat(status(group)).isEqualTo("11");
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM sys_attachment WHERE id=?", Integer.class, attachmentId)).isZero();
        assertThat(jdbc.queryForObject("SELECT adjust_reason FROM ip_adjust_log_stock WHERE id=?", String.class, logId)).isEqualTo("批量调整原因");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_step_stock WHERE step_status='pending' AND handler_id='1'", Integer.class)).isEqualTo(1);
        assertThat(count("ip_pool_status_stock")).isZero();
    }

    /** 内部报告不能在批量驳回修改中被替换为本地报告，失败上传文件也须清理。 */
    @Test public void batchRejectedModifyShouldRejectReplacingInternalReportWithLocalFile() throws Exception {
        // 初始批量申请以有效内部研究报告满足要求。
        sql("UPDATE ip_investment_pool SET in_report_restriction='internal' WHERE id=1",
                "INSERT INTO rrs_report_in(id,security_code,security_type,is_deleted) VALUES(7,'600001.SH','stock',0)");
        attachment(7L, "rrs_report_in", 7L, "report_in");
        BatchStockAdjustReq request = batchRequest("600001.SH"); request.getItems().get(0).setReportSourceAttachmentIds(Collections.singletonList(7L));
        batches.addAdjustLog(request);
        // 在发起人修改待办删除原内部引用并尝试只上传本地文件。
        StockAdjustSubmitDto group = batchGroup("600001.SH");
        audit.submitAdjustAudit(auditReq(group, "5", "reject"));
        Long logId = group.getAdjustLogIds().get(0);
        Long boundId = jdbc.queryForObject("SELECT id FROM sys_attachment WHERE table_name='ip_adjust_log_stock' AND main_id=?", Long.class, logId);
        StockPoolAdjustAuditReq modify = auditReq(group, "1", "approve");
        StockPoolAdjustAuditReq.AttachmentChange change = new StockPoolAdjustAuditReq.AttachmentChange();
        change.setAdjustLogId(logId); change.setDeleteAttachmentIds(Collections.singletonList(boundId));
        change.setReportFileIndexes(Collections.singletonList(0)); modify.setAttachmentChanges(Collections.singletonList(change));
        MockMultipartFile file = new MockMultipartFile("files", "local.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> audit.submitAdjustAudit(modify, Collections.singletonList(file), "[\"本地替换报告.pdf\"]"))
                .isInstanceOf(BizException.class).hasMessageContaining("报告不符合");
        assertThat(status(group)).isEqualTo("11");
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM sys_attachment WHERE id=?", Integer.class, boundId)).isZero();
        assertThat(count("sys_attachment")).isEqualTo(2);
        assertThat(count("ip_pool_status_stock")).isZero();
        // 修改失败后删除新上传物理文件，原内部报告引用保持有效。
        assertThat(storedFiles()).isEmpty();
    }

    /** 一般主项的关系池只配置快速流程时，批量仍继承主项一般流程完成整组审批。 */
    @Test public void batchRelationsShouldInheritNormalMainFlowWithoutOwnNormalConfiguration() {
        // 关系项不单独选择审批流程，只保留主项流程及关系池业务约束。
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'in_linked',2,0)",
                "UPDATE ip_investment_pool SET in_flow_id=NULL,in_flow_key=NULL WHERE id=2");
        BatchStockAdjustDto submitted = batches.addAdjustLog(batchRequest("600001.SH"));
        assertThat(submitted.getSubmitCount()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT flow_id FROM ip_adjust_log_stock", Long.class)).containsOnly(10L);
        assertThat(jdbc.queryForList("SELECT flow_type FROM ip_adjust_log_stock", String.class)).containsOnly("normalInbound");
        // 真实审批只从手工主项发起，关系项一并落池。
        StockAdjustSubmitDto group = batchGroup("600001.SH");
        audit.submitAdjustAudit(auditReq(group, "5", "approve"));
        assertThat(jdbc.queryForList("SELECT target_pool_id FROM ip_pool_status_stock WHERE is_deleted=0", Long.class)).containsExactlyInAnyOrder(1L, 2L);
    }

    /** 批量不能使用快速流程，单笔不能伪造批量渠道，既有单笔快速行为保持可用。 */
    @Test public void batchFastFlowAndSingleForgedBatchTypeShouldBeRejected() {
        // 将一般批量主项伪造为快速配置，不能创建任何运行记录。
        BatchStockAdjustReq request = batchRequest("600001.SH");
        request.getItems().get(0).setFlowId(20L); request.getItems().get(0).setFlowKey("stock_fast"); request.getItems().get(0).setFlowType("fastInbound");
        assertThatThrownBy(() -> batches.addAdjustLog(request)).isInstanceOf(BizException.class).hasMessageContaining("一般审批流程");
        // 单笔公开入口不接受客户端提供的批量类型。
        StockPoolAdjustSubmitReq forged = request("600001.SH", false, 1L); forged.setAdjustType("手动批量调整");
        assertThatThrownBy(() -> apply.addAdjustLog(forged)).isInstanceOf(BizException.class).hasMessageContaining("只支持手工申请");
        assertThat(count("ip_adjust_log_stock")).isZero();
        assertThat(count("ip_adjust_step_stock")).isZero();
        // 确认新增批量限制不会破坏合法单笔快速申请。
        StockAdjustSubmitDto single = apply.addAdjustLog(request("600001.SH", true, 1L));
        assertThat(status(single)).isEqualTo("20");
        assertThat(count("ip_pool_status_stock")).isEqualTo(1);
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
    /** 根据真实批量校验结果构造各股票完整分组，仅主项传一般流程。 */
    private BatchStockAdjustReq batchRequest(String... codes) {
        BatchStockAdjustReq request = new BatchStockAdjustReq(); request.setCurrentUserId("1"); request.setPoolId(1L); request.setDirection("in");
        request.setAdjusterId("1"); request.setAdjusterName("管理员"); request.setAdjustReason("批量调整原因");
        List<BatchStockAdjustReq.StockItem> stocks = new ArrayList<>();
        for (String code : codes) { BatchStockAdjustReq.StockItem stock = new BatchStockAdjustReq.StockItem(); stock.setStockCode(code); stocks.add(stock); }
        request.setStocks(stocks);
        List<BatchStockAdjustReq.AdjustItem> items = new ArrayList<>();
        for (StockAdjustCheckDto.CheckResultItem row : batches.checkAdjust(request).getItems()) {
            assertThat(row.isCanAdjust()).as("股票 %s 在池 %s 的校验应通过：%s", row.getStockCode(), row.getTargetPoolId(), row.getFailReasons()).isTrue();
            BatchStockAdjustReq.AdjustItem item = new BatchStockAdjustReq.AdjustItem();
            item.setStockCode(row.getStockCode()); item.setTargetPoolId(row.getTargetPoolId()); item.setTargetPoolName(row.getPoolName()); item.setPoolType(row.getPoolType());
            item.setAdjustMode(row.getAdjustMode()); item.setItemTag(row.getItemTag()); item.setAdjustGroupKey(row.getAdjustGroupKey());
            if ("manual".equals(row.getItemTag())) {
                StockAdjustCheckDto.FlowOption flow = row.getFlowOptions().stream().filter(StockAdjustCheckDto.FlowOption::isSelectable).findFirst().get();
                item.setFlowId(flow.getFlowId()); item.setFlowKey(flow.getFlowKey()); item.setFlowType(flow.getFlowType());
            }
            items.add(item);
        }
        request.setItems(items);
        return request;
    }
    /** 查询批量提交后本股票的真实日志及独立批次，复用既有审核请求助手。 */
    private StockAdjustSubmitDto batchGroup(String code) {
        StockAdjustSubmitDto group = new StockAdjustSubmitDto();
        group.setAdjustLogIds(jdbc.queryForList("SELECT id FROM ip_adjust_log_stock WHERE stock_code=? ORDER BY id", Long.class, code));
        group.setAdjustBatchNos(jdbc.queryForList("SELECT DISTINCT adjust_batch_no FROM ip_adjust_log_stock WHERE stock_code=?", String.class, code));
        return group;
    }
    /** 保持真实 Mapper 行为，同时允许观察或模拟指定写入失败。 */
    private StockPoolAdjustMapper observeAdjustments() {
        StockPoolAdjustMapper observed = mock(StockPoolAdjustMapper.class, AdditionalAnswers.delegatesTo(adjustments));
        ReflectionTestUtils.setField(apply, "stockPoolAdjustMapper", observed);
        return observed;
    }
    /** 列举临时附件目录内实际存在的物理文件。 */
    private List<Path> storedFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(uploadDirectory.getRoot().toPath())) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }
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
