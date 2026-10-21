package com.znty.rrs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.WindCodeSyncMapper;
import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import com.znty.rrs.common.enums.WindCodeChangeResult;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotCrmwBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.UUID;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 使用生产 DDL、真实 Mapper 和 Spring 事务验证临时证券 ID，不连接业务数据库。 */
public class TempSecurityCodePersistenceTest {

    /** 独立内存库查询组件 */
    private JdbcTemplate jdbc;
    /** 生产临时代码 Mapper */
    private TempSecurityCodeMapper mapper;
    /** 带事务的临时代码服务 */
    private TempSecurityCodeService service;
    /** 仅同券外部市场映射使用可控桩，其余使用生产 SQL。 */
    private SecurityMarketService markets;
    /** 真实调库 Mapper，供测试建立原申请和快照。 */
    private SecurityPoolAdjustMapper adjustMapper;
    /** 真实 CRMW 池状态及快照 Mapper。 */
    private CrmwPoolAdjustMapper crmwMapper;

    /** 真实 Wind 事件与主档 Mapper。 */
    private WindCodeSyncMapper windMapper;

    /** 创建生产表结构及真实 Mapper，市场查询使用固定正式证券的市场。 */
    @Before
    public void setUp() throws Exception {
        PageHelper.clearPage();
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:mem:temp_security_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        jdbc = new JdbcTemplate(source);
        for (String file : Arrays.asList("rrs_temp_security_code_schema.sql", "rrs_external_import_schema.sql",
                "rrs_security_pool_adjust_schema.sql", "rrs_crmw_pool_status_schema.sql",
                "rrs_pool_init_schema.sql", "rrs_adjust_snapshot_schema.sql")) {
            // 在隔离内存库使用生产列定义
            createSchema(file);
        }
        jdbc.execute("CREATE ALIAS DATE_FORMAT FOR 'com.znty.rrs.service.TempSecurityCodePersistenceTest.formatDate'");
        jdbc.execute("CREATE SCHEMA ais_inv_ods");
        jdbc.execute("CREATE TABLE ais_inv_ods.wind_cbondissuer (s_info_compcode VARCHAR(32),s_info_compname VARCHAR(200))");
        jdbc.update("INSERT INTO ais_inv_ods.wind_cbondissuer VALUES ('C10001','测试发行主体')");
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(64),security_type_name VARCHAR(100)"
                + ",category_type VARCHAR(32),is_deleted INT)");
        jdbc.update("INSERT INTO dict_security_type VALUES ('mtn','中期票据','bond',0)");
        jdbc.update("INSERT INTO rrs_securityinfo(wind_code,sec_id,short_name,security_type,security_status,security_source)"
                + " VALUES ('FORMAL.IB','OFFICIAL_ID','正式证券','mtn','L','official')");
        Configuration config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.addInterceptor(new PageInterceptor());
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), source));
        for (String name : Arrays.asList("TempSecurityCodeMapper", "SecurityPoolAdjustMapper", "CrmwPoolAdjustMapper", "WindCodeSyncMapper")) {
            try (InputStream xml = getClass().getResourceAsStream("/mapper/" + name + ".xml")) {
                new XMLMapperBuilder(xml, config, name + ".xml", config.getSqlFragments()).parse();
            }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        mapper = session.getMapper(TempSecurityCodeMapper.class);
        markets = mock(SecurityMarketService.class);
        when(markets.queryMarketCode("FORMAL.IB")).thenReturn("NIB");
        adjustMapper = session.getMapper(SecurityPoolAdjustMapper.class);
        TempSecurityCodeService target = new TempSecurityCodeService();
        ReflectionTestUtils.setField(target, "tempSecurityCodeMapper", mapper);
        ReflectionTestUtils.setField(target, "securityMarketService", markets);
        windMapper = session.getMapper(WindCodeSyncMapper.class);
        ReflectionTestUtils.setField(target, "windCodeSyncMapper", windMapper);
        TempSecurityCodeBusinessConverter converter = new TempSecurityCodeBusinessConverter();
        ReflectionTestUtils.setField(converter, "tempSecurityCodeMapper", mapper);
        ReflectionTestUtils.setField(converter, "securityPoolAdjustMapper", adjustMapper);
        crmwMapper = session.getMapper(CrmwPoolAdjustMapper.class);
        ReflectionTestUtils.setField(converter, "crmwPoolAdjustMapper", crmwMapper);
        ReflectionTestUtils.setField(converter, "securityMarketService", markets);
        ReflectionTestUtils.setField(target, "businessConverter", converter);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (TempSecurityCodeService) proxy.getProxy();
    }

    /** 释放隔离内存库并清理分页线程状态。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    /** official 主档原地更名，只补登记，重复事件与连续更名均不重复写入。 */
    @Test
    public void windOfficialShouldRenameOnceAndPreserveIdentityAcrossChain() {
        when(markets.queryMarketCode("NEW.IB")).thenReturn("NIB");
        when(markets.queryMarketCode("FINAL.IB")).thenReturn("NIB");
        WindCodeChangeDto first = windChange("E1", "OFFICIAL_ID", "FORMAL.IB", "NEW.IB");
        assertThat(service.editWindCodeChangeByJob(first)).isEqualTo(WindCodeChangeResult.UPDATED);
        assertThat(service.editWindCodeChangeByJob(first)).isEqualTo(WindCodeChangeResult.ALREADY_COMPLETED);
        assertThat(service.editWindCodeChangeByJob(windChange("E2", "OFFICIAL_ID", "NEW.IB", "FINAL.IB")))
                .isEqualTo(WindCodeChangeResult.UPDATED);
        assertThat(service.editWindCodeChangeByJob(first)).isEqualTo(WindCodeChangeResult.ALREADY_COMPLETED);
        assertThat(jdbc.queryForObject("SELECT wind_code FROM rrs_securityinfo WHERE sec_id='OFFICIAL_ID'", String.class))
                .isEqualTo("FINAL.IB");
        assertThat(jdbc.queryForObject("SELECT security_source FROM rrs_securityinfo WHERE sec_id='OFFICIAL_ID'", String.class))
                .isEqualTo("official");
        assertThat(count("SELECT COUNT(*) FROM rrs_securityinfo WHERE sec_id='OFFICIAL_ID'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code WHERE status='updated' AND oprt_source='job'"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isZero();
    }

    /** temporary 复用原人工登记，原备注及登记证券 ID 保留，新主档正常可用。 */
    @Test
    public void windTemporaryShouldReuseRegistrationAndConvertSource() throws Exception {
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        jdbc.update("UPDATE rrs_securityinfo SET sec_id='REAL_ID' WHERE wind_code='001.IB'");
        jdbc.update("UPDATE rrs_temp_security_code SET memo='原始备注' WHERE id=?", temp.getId());
        when(markets.queryMarketCode("001.IB")).thenReturn("NIB");
        when(markets.queryMarketCode("NEW.IB")).thenReturn("NIB");
        assertThat(service.editWindCodeChangeByJob(windChange("E1", "REAL_ID", "001.IB", "NEW.IB")))
                .isEqualTo(WindCodeChangeResult.UPDATED);
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code")).isEqualTo(1);
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("updated");
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getMemo()).startsWith("原始备注\n[wind_code_sync:");
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getSecId()).isEqualTo("TEMP_001.IB");
        assertThat(jdbc.queryForObject("SELECT security_source FROM rrs_securityinfo WHERE sec_id='REAL_ID'", String.class))
                .isEqualTo("temp_converted");
        assertThat(jdbc.queryForObject("SELECT market FROM rrs_securityinfo WHERE sec_id='REAL_ID'", String.class)).isEqualTo("NIB");
        assertThat(jdbc.queryForObject("SELECT security_status FROM rrs_securityinfo WHERE sec_id='REAL_ID'", String.class)).isEqualTo("L");
    }

    /** 主档缺失只返回跳过结果，不补登记、主档或池流水。 */
    @Test
    public void windMissingMasterShouldNotWriteAnything() {
        assertThat(service.editWindCodeChangeByJob(windChange("E1", "MISSING", "OLD.IB", "NEW.IB")))
                .isEqualTo(WindCodeChangeResult.MISSING_MASTER);
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code")).isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isZero();
    }

    /** temp_converted 新增本次登记，原临时码及历史目标不回退、不覆盖。 */
    @Test
    public void windConvertedShouldKeepPreviousRegistration() {
        jdbc.update("UPDATE rrs_securityinfo SET security_source='temp_converted' WHERE wind_code='FORMAL.IB'");
        jdbc.update("INSERT INTO rrs_temp_security_code(temp_security_code,security_code,status,is_deleted,oprt_source)"
                + " VALUES('ORIGINAL_TMP','FORMAL.IB','updated',0,'manual')");
        when(markets.queryMarketCode("NEW.IB")).thenReturn("NIB");
        service.editWindCodeChangeByJob(windChange("E1", "OFFICIAL_ID", "FORMAL.IB", "NEW.IB"));
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code WHERE temp_security_code='ORIGINAL_TMP'"
                + " AND security_code='FORMAL.IB' AND status='updated' AND oprt_source='manual'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code WHERE temp_security_code='FORMAL.IB'"
                + " AND security_code='NEW.IB' AND status='updated' AND oprt_source='job'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT security_source FROM rrs_securityinfo WHERE wind_code='NEW.IB'", String.class))
                .isEqualTo("temp_converted");
    }

    /** 单码换码迁移普通池、CRMW 标的和原在途申请，不扩展已有同券其他市场。 */
    @Test
    public void windShouldConvertBothPoolKindsAndKeepPendingApproval() {
        // 建立同券其他市场，确认 Wind 入口不会扩展或重调它们。
        setupGroup();
        when(markets.queryMarketCode("NEW.IB")).thenReturn("NIB");
        // 配置普通池并建立原状态、调出申请。
        addPool(100L, "[]", 10);
        // 原正式码和另一市场代码均已在池。
        addActive("FORMAL.IB", 100L);
        // 其他市场持仓必须保持原状。
        addActive("FORMAL.SH", 100L);
        // 保留原审批身份和状态。
        IpAdjustLogBo pending = addPending("FORMAL.IB", 100L, "调出", "11");
        // 同时建立 CRMW 标的持仓。
        addCrmwPool(200L, "[]");
        // 两个凭证各自持有同一个标的，均应独立转换。
        addCrmwState("FORMAL.IB", "K.IB", 200L);
        // 不能因另一凭证持有新码而省略本凭证入池。
        addCrmwState("FORMAL.IB", "K2.IB", 200L);
        service.editWindCodeChangeByJob(windChange("E1", "OFFICIAL_ID", "FORMAL.IB", "NEW.IB"));
        assertThat(activeCodes(100L)).containsExactly("FORMAL.SH", "NEW.IB");
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE security_code='NEW.IB' AND is_deleted=0")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE security_code='FORMAL.IB' AND adjust_mode='调出'"
                + " AND audit_status='20' AND adjuster_id='0' AND adjust_advice IS NULL")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE security_code='NEW.IB' AND adjust_mode='调入'"
                + " AND audit_status='20' AND adjuster_id='0' AND adjust_advice IS NULL")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=?", String.class, pending.getId()))
                .isEqualTo("NEW.IB");
        assertThat(jdbc.queryForObject("SELECT audit_status FROM ip_adjust_log WHERE id=?", String.class, pending.getId()))
                .isEqualTo("11");
        assertThat(jdbc.queryForObject("SELECT flow_id FROM ip_adjust_log WHERE id=?", Long.class, pending.getId())).isEqualTo(9L);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot WHERE wind_code='NEW.IB'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw WHERE wind_code='NEW.IB'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE security_code='FORMAL.IB' AND adjust_batch_no='HISTORY'"))
                .isEqualTo(1);
    }

    /** 实际写入失败后主档、登记、调库流水和软删池状态全部回滚。 */
    @Test
    public void windShouldRollbackMasterAndBusinessWritesOnAuditFailure() {
        when(markets.queryMarketCode("NEW.IB")).thenReturn("NIB");
        // 建立原在池状态，使失败发生在已写出库流水及软删之后。
        addPool(100L, "[]", 10);
        // 生成原持仓及历史日志。
        addActive("FORMAL.IB", 100L);
        jdbc.execute("ALTER TABLE rrs_temp_security_code_update_log ADD CONSTRAINT reject_wind_audit CHECK(replace_status <> 'success')");
        assertThatThrownBy(() -> service.editWindCodeChangeByJob(windChange("E1", "OFFICIAL_ID", "FORMAL.IB", "NEW.IB")))
                .isInstanceOf(RuntimeException.class);
        assertThat(activeCodes(100L)).containsExactly("FORMAL.IB");
        assertThat(count("SELECT COUNT(*) FROM rrs_securityinfo WHERE wind_code='FORMAL.IB' AND sec_id='OFFICIAL_ID'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code")).isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(1);
    }

    /** 无效日期及登记缺失必须明确拒绝，不能部分更名或补造人工登记。 */
    @Test
    public void windShouldRejectInvalidDateAndMissingTemporaryRegistration() {
        WindCodeChangeDto invalid = windChange("E1", "OFFICIAL_ID", "FORMAL.IB", "NEW.IB");
        invalid.setChangeDate("20260230");
        assertThatThrownBy(() -> service.editWindCodeChangeByJob(invalid)).hasMessageContaining("无效");
        jdbc.update("UPDATE rrs_securityinfo SET security_source='temporary' WHERE wind_code='FORMAL.IB'");
        when(markets.queryMarketCode("FORMAL.IB")).thenReturn("NIB");
        assertThatThrownBy(() -> service.editWindCodeChangeByJob(windChange("E1", "OFFICIAL_ID", "FORMAL.IB", "NEW.IB")))
                .hasMessageContaining("缺少有效登记");
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code")).isZero();
    }

    /** 使用生产 ODS 定义及真实 SQL 验证日期边界、相同代码过滤和结果映射。 */
    @Test
    public void windScannerShouldUseEightDigitDateWindow() throws Exception {
        String ddl = new String(Files.readAllBytes(Paths.get("sql/ais_inv_ods_schema.sql")), StandardCharsets.UTF_8);
        Matcher create = Pattern.compile("CREATE TABLE `wind_cbondchangewindcode`.*?;", Pattern.DOTALL).matcher(ddl);
        assertThat(create.find()).isTrue();
        jdbc.execute(create.group().replaceFirst("(?s)\\)\\s*ENGINE.*$", ")")
                .replace("CREATE TABLE `wind_cbondchangewindcode`", "CREATE TABLE ais_inv_ods.`wind_cbondchangewindcode`"));
        for (String date : Arrays.asList("20261018", "20261019", "20261020", "20261021")) {
            jdbc.update("INSERT INTO ais_inv_ods.wind_cbondchangewindcode(OBJECT_ID,SEC_ID,S_INFO_OLDWINDCODE,S_INFO_NEWWINDCODE,CHANGE_DATE)"
                    + " VALUES(?,?,?,?,?)", date, "ID", "A.IB", "B.IB", date);
        }
        jdbc.update("INSERT INTO ais_inv_ods.wind_cbondchangewindcode(OBJECT_ID,S_INFO_OLDWINDCODE,S_INFO_NEWWINDCODE,CHANGE_DATE)"
                + " VALUES('SAME','A.IB','A.IB','20261020')");
        assertThat(windMapper.queryRecentWindCodeChangeList("20261019", "20261020"))
                .extracting(WindCodeChangeDto::getChangeDate).containsExactly("20261019", "20261020");
        assertThat(windMapper.queryRecentWindCodeChangeList("20261019", "20261020").get(0).getOldWindCode()).isEqualTo("A.IB");
    }

    /** 并发执行同一事件由主档行锁串行化，只生成一次登记和出入库流水。 */
    @Test
    public void windConcurrentRetriesShouldCommitOnlyOnce() throws Exception {
        when(markets.queryMarketCode("NEW.IB")).thenReturn("NIB");
        // 建立可检验实际流水数量的原持仓。
        addPool(100L, "[]", 10);
        // 使用真实 Mapper 建立历史状态。
        addActive("FORMAL.IB", 100L);
        // 两个线程通过真实事务代理处理同一事件。
        WindCodeChangeDto event = windChange("E1", "OFFICIAL_ID", "FORMAL.IB", "NEW.IB");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<WindCodeChangeResult> first = executor.submit(() -> {
                start.await();
                return service.editWindCodeChangeByJob(event);
            });
            Future<WindCodeChangeResult> second = executor.submit(() -> {
                start.await();
                return service.editWindCodeChangeByJob(event);
            });
            start.countDown();
            assertThat(Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(WindCodeChangeResult.UPDATED, WindCodeChangeResult.ALREADY_COMPLETED);
        } finally {
            executor.shutdownNow();
        }
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjuster_id='0'")).isEqualTo(2);
        assertThat(activeCodes(100L)).containsExactly("NEW.IB");
    }

    /** 登记目标冲突或转正历史缺失时不覆盖原登记、不更名主档。 */
    @Test
    public void windShouldRejectConflictingRegistrationAndMissingConversionHistory() {
        when(markets.queryMarketCode("NEW.IB")).thenReturn("NIB");
        jdbc.update("INSERT INTO rrs_temp_security_code(temp_security_code,temp_security_type,temp_security_market,security_code,status,is_deleted)"
                + " VALUES('FORMAL.IB','mtn','NIB','OTHER.IB','temporary',0)");
        // 构造与已确定目标冲突的 Wind 事件。
        WindCodeChangeDto event = windChange("E1", "OFFICIAL_ID", "FORMAL.IB", "NEW.IB");
        assertThatThrownBy(() -> service.editWindCodeChangeByJob(event)).hasMessageContaining("目标代码与原登记冲突");
        assertThat(count("SELECT COUNT(*) FROM rrs_securityinfo WHERE wind_code='FORMAL.IB'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code WHERE security_code='OTHER.IB' AND status='temporary'")).isEqualTo(1);
        jdbc.update("UPDATE rrs_securityinfo SET security_source='temp_converted' WHERE wind_code='FORMAL.IB'");
        assertThatThrownBy(() -> service.editWindCodeChangeByJob(event)).hasMessageContaining("缺少对应历史登记");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isZero();
    }

    /** 构造具有稳定事件 ID 的 Wind 变更。 */
    private WindCodeChangeDto windChange(String eventId, String secId, String oldCode, String newCode) {
        WindCodeChangeDto change = new WindCodeChangeDto();
        change.setObjectId(eventId);
        change.setSecId(secId);
        change.setOldWindCode(oldCode);
        change.setNewWindCode(newCode);
        change.setChangeDate("20261021");
        return change;
    }

    /** 新增额外写入双表 ID，原证券代码和接口返回字段保持不变。 */
    @Test
    public void addShouldPersistSameIdInRegistrationAndMaster() throws Exception {
        // 通过实际服务新增，证券代码本身不拼前缀
        TempSecurityCodeDto added = service.addTempSecurityCode(addReq("001.IB"));
        assertThat(added.getTempSecurityCode()).isEqualTo("001.IB");
        assertThat(new ObjectMapper().valueToTree(added).has("secId")).isFalse();
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE id=?", String.class, added.getId()))
                .isEqualTo("TEMP_001.IB");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='001.IB'", String.class))
                .isEqualTo("TEMP_001.IB");
    }

    /** 实际占位 INSERT 失败时，已经插入的登记记录由事务回滚。 */
    @Test
    public void addShouldRollbackRegistrationWhenMasterInsertFails() throws Exception {
        jdbc.execute("ALTER TABLE rrs_securityinfo ADD CONSTRAINT fail_test_code CHECK (wind_code <> 'FAIL.IB')");
        // 触发内存库的实际占位写入失败
        TempSecurityCodeReq failed = addReq("FAIL.IB");
        assertThatThrownBy(() -> service.addTempSecurityCode(failed)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_securityinfo WHERE wind_code='FAIL.IB'", Integer.class))
                .isZero();
    }

    /** 人工转正保留临时双表 ID，且不会覆盖正式证券的独立 ID。 */
    @Test
    public void manualConversionShouldPreserveTemporaryAndFormalIds() throws Exception {
        // 创建临时登记并通过正式选券进行转正
        TempSecurityCodeDto added = service.addTempSecurityCode(addReq("001.IB"));
        // 构造仅含主键和正式代码的状态请求
        TempSecurityCodeDto updated = service.editTempSecurityCodeToUpdated(operation(added.getId(), "FORMAL.IB"));
        assertThat(updated.getStatus()).isEqualTo("updated");
        // 验证主档禁用不改 ID，正式证券不被覆盖
        assertMasterIds();
    }

    /** 定时转正使用外部补齐的正式代码，并保留临时和正式 ID。 */
    @Test
    public void jobConversionShouldPreserveTemporaryAndFormalIds() throws Exception {
        // 建立等待外部补齐正式代码的临时记录
        TempSecurityCodeDto added = service.addTempSecurityCode(addReq("001.IB"));
        jdbc.update("UPDATE rrs_temp_security_code SET security_code='FORMAL.IB' WHERE sec_id='TEMP_001.IB'");
        assertThat(mapper.queryJobReadyTempSecurityCodeList()).extracting("id").containsExactly(added.getId());
        TempSecurityCodeDto updated = service.editTempSecurityCodeToUpdatedByJob(added.getId());
        assertThat(updated.getStatus()).isEqualTo("updated");
        assertThat(updated.getOprtSource()).isEqualTo("job");
        // 验证定时入口同样不改两类主档 ID
        assertMasterIds();
    }

    /** 取消发行及软删除仅改变状态，保留外部使用的证券 ID。 */
    @Test
    public void cancelAndDeleteShouldPreserveIds() throws Exception {
        // 创建待取消的临时代码
        TempSecurityCodeDto cancelled = service.addTempSecurityCode(addReq("CANCEL.IB"));
        // 取消后读取实际 ID
        TempSecurityCodeDto result = service.editTempSecurityCodeToCancelled(operation(cancelled.getId(), null));
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE id=?", String.class, cancelled.getId()))
                .isEqualTo("TEMP_CANCEL.IB");
        assertThat(result.getStatus()).isEqualTo("cancelled");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='CANCEL.IB'", String.class))
                .isEqualTo("TEMP_CANCEL.IB");
        // 创建另一条可软删除的临时代码
        TempSecurityCodeDto deleted = service.addTempSecurityCode(addReq("DELETE.IB"));
        // 执行真实软删除并读取已删除列表记录
        service.deleteTempSecurityCode(operation(deleted.getId(), null));
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE id=?", String.class, deleted.getId()))
                .isEqualTo("TEMP_DELETE.IB");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='DELETE.IB'", String.class))
                .isEqualTo("TEMP_DELETE.IB");
    }

    /** 原始 Demo 的临时登记、占位主档 ID 一致，已转正示例仍保留临时 ID。 */
    @Test
    public void demoShouldKeepRegistrationAndPlaceholderIdsConsistent() throws Exception {
        // 在隔离内存库执行两份 Demo 的原始 INSERT，不执行清空或业务库初始化
        executeDemoInsert("rrs_temp_security_code_demo_data.sql", "rrs_temp_security_code");
        // 读取实际证券主档样例
        executeDemoInsert("rrs_external_import_demo_data.sql", "rrs_securityinfo");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code"
                + " WHERE sec_id IS NULL OR sec_id <> CONCAT('TEMP_',temp_security_code)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_securityinfo s"
                + " JOIN rrs_temp_security_code t ON t.temp_security_code=s.wind_code"
                + " WHERE s.security_source='temporary' AND s.sec_id=t.sec_id", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE status='updated'", String.class))
                .isEqualTo("TEMP_TMP002.IB");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='ABN002.IB'", String.class))
                .doesNotStartWith("TEMP_");
    }

    /** 三个市场分别记账，所有即时记录共用批次。 */
    @Test
    public void ordinaryConversionShouldMoveWholeGroupWithOneBatch() throws Exception {
        // 建立三个市场的整组转换场景
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 3);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).containsExactly("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ");
        // 即时转换沿用原直通语义，不把历史审批流程挂到本次新批次
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_reason LIKE '%代码%' AND flow_id IS NOT NULL"))
                .isZero();
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE security_code='001.IB' AND adjust_mode='调出'"))
                .isEqualTo(1);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_type='关联调整' AND audit_status='20'"))
                .isEqualTo(2);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log WHERE adjust_reason LIKE '%代码%'")).isEqualTo(1);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot WHERE wind_code LIKE 'FORMAL.%'"))
                .isEqualTo(3);
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getOprtSource()).isEqualTo("manual");
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code_update_log"))
                .isEqualTo(4);
        // 通过转正入口提交选中的正式代码
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("只有临时状态");
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).hasSize(3);
    }

    /** 无运行态业务不凭空给同券市场生成调库记录。 */
    @Test
    public void groupWithoutBusinessShouldOnlyConvertIdentity() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isZero();
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status")).isZero();
    }

    /** 正式组全部在池时只清理临时代码，不重复生成正式组调入记录。 */
    @Test
    public void completeExistingGroupShouldOnlyRemoveTemporaryState() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 建立转换前真实的在池状态
        addActive("FORMAL.IB", 100L);
        // 建立转换前真实的在池状态
        addActive("FORMAL.SH", 100L);
        // 建立转换前真实的在池状态
        addActive("FORMAL.SZ", 100L);
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).hasSize(3);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(5);
    }

    /** 不自动补修部分市场在池的数据异常，登记状态也不能改变。 */
    @Test
    public void partialExistingGroupShouldRejectWithoutWrites() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 建立转换前真实的在池状态
        addActive("FORMAL.IB", 100L);
        // 通过转正入口提交选中的正式代码
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("未整组在池");
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(2);
    }

    /** 在途调入共享原审批批次和快照信息，不创建池状态和审批步骤。 */
    @Test
    public void pendingInboundShouldExpandWithoutApproving() throws Exception {
        // 验证普通审批中状态
        assertPendingInbound("00");
    }

    /** 驳回待修改的原申请扩展后依旧待修改。 */
    @Test
    public void rejectedEditableInboundShouldKeepOriginalStatus() throws Exception {
        // 验证驳回待修改状态不会被转正重新发起
        assertPendingInbound("11");
    }

    /** 当前状态先转正，原调出申请只迁移身份并保持审批中。 */
    @Test
    public void activeWithPendingOutboundShouldKeepGroupUntilApproval() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 建立需要保留审批进度的原申请
        IpAdjustLogBo pending = addPending("001.IB", 100L, "调出", "00");
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).hasSize(3);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='ORIGINAL' AND audit_status='00' AND adjust_mode='调出'"))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=?", String.class, pending.getId()))
                .isEqualTo("FORMAL.IB");
    }

    /** 多个原池分别承接整组，所有即时转换共用一次批次。 */
    @Test
    public void multiplePoolsShouldConvertAtomically() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 配置本场景的普通目标池及容量
        addPool(101L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 建立转换前真实的在池状态
        addActive("001.IB", 101L);
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).hasSize(3);
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(101L)).hasSize(3);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log WHERE adjust_reason LIKE '%代码%'"))
                .isEqualTo(1);
    }

    /** 不能按池市场过滤掉部分代码后让其余代码入池。 */
    @Test
    public void unsupportedMemberMarketShouldRejectWholeConversion() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[\"NIB\"]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 通过转正入口提交选中的正式代码
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("市场不符合").hasMessageContaining("FORMAL.SH")
                .hasMessageContaining("投资池：测试普通池").hasMessageNotContaining("poolId=");
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).containsExactly("001.IB");
    }

    /** 转正只迁移证券身份，容量配置不阻断正式组承接。 */
    @Test
    public void configuredCapacityShouldNotBlockIdentityConversion() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 2);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).containsExactly("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ");
    }

    /** 目标池启停和锁定是调库准入条件，不重新阻断代码身份迁移。 */
    @Test
    public void disabledLockedPoolShouldStillConvertIdentity() throws Exception {
        // 建立同券正式代码组
        setupGroup();
        // 原池仍保存业务状态，但配置已经停用并锁定
        addPool(100L, "[]", 2);
        jdbc.update("UPDATE ip_investment_pool SET status='disabled',lock_flag=1 WHERE id=100");
        // 登记临时代码并建立原在池记录
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 迁移原状态，不重跑池的启停、锁定与容量准入
        addActive("001.IB", 100L);
        // 执行正式代码整组转换
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 三个正式代码一起承接原池状态
        assertThat(activeCodes(100L)).containsExactly("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ");
    }

    /** 已完整持有正式组时，只移除T，不重跑旧持仓的市场或池关系准入。 */
    @Test
    public void existingGroupCleanupShouldNotRecheckInboundRules() throws Exception {
        // 建立同券正式代码组
        setupGroup();
        // P市场配置已变化，但正式组和T仍保留当前状态
        addPool(100L, "[\"NIB\"]", 1);
        // Q与P原有正式持仓并存，本次不新增任何正式持仓
        addPool(101L, "[]", 10);
        // 登记需要清理的临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立T原状态
        addActive("001.IB", 100L);
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 正式组在P已经完整在池
            addActive(code, 100L);
            // 正式组在Q也已经完整在池
            addActive(code, 101L);
        }
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'in_mutex',101,0)");
        // 只移除T，不因旧持仓配置变化阻断清理
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // P与Q保留原正式组，不重复生成正式调入记录
        assertThat(activeCodes(100L)).hasSize(3);
        // 核对另一池保持原状态
        assertThat(activeCodes(101L)).hasSize(3);
        // 原7条记录只新增T调出一条
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(8);
    }

    /** 同券关联代码缺主数据时保留错误定位，不静默跳过。 */
    @Test
    public void missingRelatedMasterShouldRejectWholeConversion() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        jdbc.update("DELETE FROM rrs_securityinfo WHERE wind_code='FORMAL.SZ'");
        // 通过转正入口提交选中的正式代码
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("FORMAL.SZ");
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).containsExactly("001.IB");
    }

    /** 正式关联代码有独立申请时不得静默并入原流程。 */
    @Test
    public void independentPendingApplicationShouldRejectConversion() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 建立需要保留审批进度的原申请
        addPending("FORMAL.SH", 100L, "调入", "00");
        // 通过转正入口提交选中的正式代码
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("正式代码在信用债大库存在流程中数据").hasMessageContaining("FORMAL.SH")
                .hasMessageContaining("投资池：测试普通池").hasMessageNotContaining("poolId=");
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).containsExactly("001.IB");
    }

    /** 非信用债大库的独立在途申请不阻断本次身份转换，也不被自动合并。 */
    @Test
    public void nonCreditPendingApplicationShouldRemainIndependent() throws Exception {
        // 建立同券正式代码组
        setupGroup();
        // 配置本次转换目标池
        addPool(100L, "[]", 10);
        // 另一个观察池不属于信用债大库
        addPool(101L, "[]", 10);
        jdbc.update("UPDATE ip_investment_pool SET pool_type='observe' WHERE id=101");
        // 登记临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立原在池状态
        addActive("001.IB", 100L);
        // 创建另一池的独立申请，保留其原主键和流程
        IpAdjustLogBo independent = addPending("FORMAL.SH", 101L, "调入", "00");
        jdbc.update("UPDATE ip_adjust_log SET pool_type='observe' WHERE id=?", independent.getId());
        // 本次更新只迁移T在目标池的状态
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 正式组正常承接目标池
        assertThat(activeCodes(100L)).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=?", String.class, independent.getId()))
                .isEqualTo("FORMAL.SH");
        // 原独立审批仍保留一条，不被扩成新的整组申请
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE target_pool_id=101 AND audit_status='00'"))
                .isEqualTo(1);
    }

    /** 无关池的数据异常不阻断本次转换，也不在转正过程中修补无关数据。 */
    @Test
    public void unrelatedPartialPoolShouldNotBlockConversion() throws Exception {
        // 建立同券正式代码组
        setupGroup();
        // 本次转换只涉及P
        addPool(100L, "[]", 10);
        // 无关Q不配置与P的池关系
        addPool(101L, "[]", 10);
        // 登记临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立P中的T状态
        addActive("001.IB", 100L);
        // 无关Q的残缺数据留待单独治理
        addActive("FORMAL.IB", 101L);
        // 本次仅转换P
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // P整组转换完成
        assertThat(activeCodes(100L)).hasSize(3);
        // Q保持原样，不自动修补
        assertThat(activeCodes(101L)).containsExactly("FORMAL.IB");
    }

    /** 后续代码的真实数据库写入失败时，主状态和之前已写的日志全部回滚。 */
    @Test
    public void laterMemberWriteFailureShouldRollbackAllBusinessData() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        jdbc.execute("ALTER TABLE ip_adjust_security_snapshot ADD CONSTRAINT fail_later_member CHECK (wind_code <> 'FORMAL.SZ')");
        // 通过转正入口提交选中的正式代码
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .isInstanceOf(RuntimeException.class);
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).containsExactly("001.IB");
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(1);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot")).isZero();
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code_update_log")).isZero();
        assertThat(jdbc.queryForObject("SELECT security_status FROM rrs_securityinfo WHERE wind_code='001.IB'", String.class))
                .isEqualTo("L");
    }

    /** 定时入口同样整组转正，自动新增的日志不填写调整建议。 */
    @Test
    public void jobShouldConvertAllMarketsAndKeepSource() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        jdbc.update("UPDATE rrs_temp_security_code SET security_code='FORMAL.IB' WHERE id=?", temp.getId());
        service.editTempSecurityCodeToUpdatedByJob(temp.getId());
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).hasSize(3);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_reason LIKE '%代码%' AND adjust_advice IS NOT NULL"))
                .isZero();
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getOprtSource()).isEqualTo("job");
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code_update_log"))
                .isEqualTo(4);
    }

    /** 转正检查硬限制关系，不能因补齐其他市场而形成双池。 */
    @Test
    public void poolRelationConflictShouldRejectConversion() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 配置本场景的普通目标池及容量
        addPool(101L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 建立转换前真实的在池状态
        addActive("001.IB", 101L);
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'in_mutex',101,0)");
        // 通过转正入口提交选中的正式代码
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("池关系冲突");
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).containsExactly("001.IB");
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(101L)).containsExactly("001.IB");
    }

    /** 临时码申请调入五级库，正式组已在一级库：补同批互斥调出，审批前不动持仓。 */
    @Test
    public void pendingInboundShouldAppendMutexOutAndApplyTogetherOnApproval() throws Exception {
        // 核对流程中状态的整组申请，并模拟原审批的批次落池操作
        assertPendingMutexTransfer("00", true);
    }

    /** 驳回待修改的原申请同样补互斥项，保留待修改状态和原审批步骤。 */
    @Test
    public void pendingModificationShouldAppendMutexOutWithoutApproving() throws Exception {
        // 不提前审批待修改申请
        assertPendingMutexTransfer("11", false);
    }

    /** 池关系有方向，其他池配置目标池为调入限制池时，不反向阻止本次调入。 */
    @Test
    public void reverseInboundRestrictionShouldNotBlockConversion() throws Exception {
        // 建立正式组当前在一级库、临时码申请调入五级库的场景
        setupGroup();
        addPool(100L, "[]", 10);
        addPool(101L, "[]", 10);
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 正式组原持仓
            addActive(code, 101L);
        }
        // 原临时代码调入申请
        addPending("001.IB", 100L, "调入", "00");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(101,'in_restrict',100,0)");
        // 仅按目标池自身配置判断，不自动生成反向调出
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(activeCodes(101L)).hasSize(3);
        assertThat(activeCodes(100L)).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='ORIGINAL'")).isEqualTo(3);
    }

    /** 目标池自身的调入限制仍阻断转换，不能被当成互斥配套调出。 */
    @Test
    public void inboundRestrictionShouldStillRejectConversion() throws Exception {
        // 准备受调入限制约束的正式组
        setupGroup();
        addPool(100L, "[]", 10);
        addPool(101L, "[]", 10);
        jdbc.update("UPDATE ip_investment_pool SET pool_name='五级库' WHERE id=100");
        jdbc.update("UPDATE ip_investment_pool SET pool_name='一级库' WHERE id=101");
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 正式组在限制池中
            addActive(code, 101L);
        }
        // 原临时申请尚未审批
        addPending("001.IB", 100L, "调入", "00");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'in_restrict',101,0)");
        // 硬限制必须拒绝，登记和原申请均不改变
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("池关系冲突").hasMessageContaining("投资池：五级库")
                .hasMessageContaining("关联投资池：一级库").hasMessageNotContaining("poolId=")
                .hasMessageNotContaining("relationPoolId=");
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE security_code='001.IB'")).isEqualTo(1);
    }

    /** 原批次已经带有互斥调出时，仅将原申请整组换码，不重复补调出。 */
    @Test
    public void existingPendingMutexOutShouldNotBeDuplicated() throws Exception {
        // T与正式组均在原池，原审批已经包含转池的出入两项
        setupGroup();
        addPool(100L, "[]", 10);
        addPool(101L, "[]", 10);
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // T原持仓需要清理
        addActive("001.IB", 101L);
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 正式组已在原池
            addActive(code, 101L);
        }
        // 同一原批次的调入和配套调出
        addPending("001.IB", 100L, "调入", "00");
        addPending("001.IB", 101L, "调出", "00");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'in_mutex',101,0)");
        // 转正只需扩展原有两条申请
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='ORIGINAL'")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='ORIGINAL' AND adjust_mode='调出'"))
                .isEqualTo(3);
        assertThat(activeCodes(101L)).hasSize(3);
        assertThat(activeCodes(100L)).isEmpty();
    }

    /** 当前已在池的T转正时，正式组原互斥持仓与新入池一起生效，操作人为系统。 */
    @Test
    public void activeConversionShouldApplyMutexOutImmediately() throws Exception {
        // 当前状态转换不创建新的审批申请
        setupGroup();
        addPool(100L, "[]", 10);
        addPool(101L, "[]", 10);
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // T在目标池
        addActive("001.IB", 100L);
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 正式组在互斥原池
            addActive(code, 101L);
        }
        jdbc.update("UPDATE ip_pool_status SET target_pool_name='一级库' WHERE target_pool_id=101");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'in_mutex',101,0)");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'source',101,0)");
        // 原池可作为来源，同时执行互斥调出
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(activeCodes(100L)).hasSize(3);
        assertThat(activeCodes(101L)).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no<>'HISTORY' AND adjuster_id='0'"
                + " AND audit_status='20'")).isEqualTo(7);
        assertThat(count("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log WHERE adjust_batch_no<>'HISTORY'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot")).isEqualTo(6);
        assertThat(jdbc.queryForList("SELECT adjust_reason FROM ip_adjust_log WHERE target_pool_id=101"
                + " AND adjust_mode='调出'", String.class))
                .hasSize(3).allSatisfy(reason -> assertThat(reason)
                        .contains("互斥配套调出池：一级库").doesNotContain("互斥配套调出池：101"));
    }

    /** 互斥配套调出池的调出限制仍须满足，不能为完成调入而绕过。 */
    @Test
    public void mutexOutRestrictionShouldRejectConversion() throws Exception {
        // 原持仓在Q和R，P调入要出Q，但Q配置在R时不可调出
        setupGroup();
        addPool(100L, "[]", 10);
        addPool(101L, "[]", 10);
        addPool(102L, "[]", 10);
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 正式组在两个相关池中
            addActive(code, 101L);
            addActive(code, 102L);
        }
        // 原调入审批尚未结束
        addPending("001.IB", 100L, "调入", "00");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'in_mutex',101,0)");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(101,'out_restrict',102,0)");
        // 配套调出受阻时整次转换失败
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("池关系冲突");
        assertThat(activeCodes(101L)).hasSize(3);
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
    }

    /** 未发生互斥调出的池，其下游关系池异常不能阻断本次转换。 */
    @Test
    public void unusedMutexPoolRelationsShouldNotBlockConversion() throws Exception {
        // Q没有正式组持仓，不会生成互斥调出，R的存量异常与本次转换无关
        setupGroup();
        addPool(100L, "[]", 10);
        addPool(101L, "[]", 10);
        addPool(102L, "[]", 10);
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 下游无关池仅有单个市场的历史异常记录
        addActive("FORMAL.IB", 102L);
        // T只申请调入P
        addPending("001.IB", 100L, "调入", "00");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'in_mutex',101,0)");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(101,'out_restrict',102,0)");
        // 本次只换码和补其他市场申请，不生成Q调出，也不修补R
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(activeCodes(102L)).containsExactly("FORMAL.IB");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='ORIGINAL'")).isEqualTo(3);
    }

    /** 新增互斥调出写入中途失败时，原申请、快照、登记与所有持仓统一回滚。 */
    @Test
    public void pendingMutexOutFailureShouldRollbackConversion() throws Exception {
        // 使用真实数据库约束注入最后一个市场的写入失败
        setupGroup();
        addPool(100L, "[]", 10);
        addPool(101L, "[]", 10);
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 原持仓不能被流程中的转换提前改变
            addActive(code, 101L);
        }
        // 原申请换码后写配套项时失败
        IpAdjustLogBo original = addPending("001.IB", 100L, "调入", "00");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'in_mutex',101,0)");
        jdbc.execute("ALTER TABLE ip_adjust_log ADD CONSTRAINT fail_mutex_out CHECK"
                + " (security_code <> 'FORMAL.SZ' OR adjust_mode <> '调出')");
        // 整次转正回滚，保留原审批供重试
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=?", String.class, original.getId()))
                .isEqualTo("001.IB");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot")).isZero();
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code_update_log")).isZero();
        assertThat(activeCodes(101L)).hasSize(3);
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
    }

    /** CRMW 同一凭证下，流程中调入补全部市场的互斥调出，不影响其他凭证。 */
    @Test
    public void crmwPendingInboundShouldAppendMutexOutForSameCredential() throws Exception {
        // K凭证从原池转入目标池，L凭证原持仓与本次无关
        TempSecurityCodeDto temp = prepareCrmwMutexInbound();
        // 转正沿用原 CRMW 审批
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        List<IpAdjustLogBo> logs = adjustMapper.queryAdjustLogListForAudit(null, "CRMW_MUTEX");
        assertThat(logs).hasSize(6);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='CRMW_MUTEX' AND crmw_scode='K.IB'"))
                .isEqualTo(6);
        assertThat(logs).extracting("flowId").containsOnly(23L);
        assertThat(logs).extracting("adjusterId").containsOnly("1");
        assertThat(logs).extracting("auditStatus").containsOnly("00");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='CRMW_MUTEX'"
                + " AND target_pool_id=201 AND adjust_mode='调出'")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE target_pool_id=201 AND is_deleted=0"))
                .isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE target_pool_id=200 AND is_deleted=0")).isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw WHERE crmw_scode='K.IB'"
                + " AND analysis='互斥转池原分析'")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_step")).isZero();
        assertThat(jdbc.queryForList("SELECT adjust_reason FROM ip_adjust_log WHERE adjust_batch_no='CRMW_MUTEX'"
                + " AND adjust_mode='调出'", String.class))
                .hasSize(3).allSatisfy(reason -> assertThat(reason)
                        .contains("调入池：CRMW目标池", "互斥配套调出池：CRMW原池")
                        .doesNotContain("调入池：200", "互斥配套调出池：201"));
    }

    /** CRMW 配套调出池已有同凭证正式标的独立流程时，也不能自动合并审批。 */
    @Test
    public void crmwMutexPoolIndependentPendingShouldRejectConversion() throws Exception {
        // 原申请本来只涉及目标池，转正后新增配套调出池也需要检查流程冲突
        TempSecurityCodeDto temp = prepareCrmwMutexInbound();
        // 正式代码在新增配套调出池另有独立申请
        IpAdjustLogBo independent = baseLog("FORMAL.IB", 201L);
        independent.setPoolType("crmw");
        independent.setCrmwScode("K.IB");
        independent.setCrmwStype("crmw");
        independent.setAuditStatus("00");
        independent.setAdjustMode("调出");
        independent.setAdjustBatchNo("INDEPENDENT");
        adjustMapper.addAdjustLog(independent);
        // 保留两条原流程，整次转正失败
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("正式代码在CRMW库存在流程中数据");
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='CRMW_MUTEX' AND security_code='001.IB'"))
                .isEqualTo(1);
    }

    /** 定时转正补互斥申请仍保留原发起人，不写调整意见。 */
    @Test
    public void jobPendingMutexOutShouldKeepApplicantAndNullAdvice() throws Exception {
        // 与人工入口使用同一组转换计划
        TempSecurityCodeDto temp = prepareCrmwMutexInbound();
        jdbc.update("UPDATE rrs_temp_security_code SET security_code='FORMAL.IB' WHERE id=?", temp.getId());
        // 定时任务执行转正
        service.editTempSecurityCodeToUpdatedByJob(temp.getId());
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='CRMW_MUTEX'"
                + " AND adjuster_id='1' AND adjust_advice IS NULL")).isEqualTo(6);
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getOprtSource()).isEqualTo("job");
    }

    /** CRMW 已在池转换的互斥出入同次生效，其他凭证的原持仓保持不变。 */
    @Test
    public void crmwActiveConversionShouldApplyMutexOutForSameCredential() throws Exception {
        // 正式标的在原池，临时标的在目标池
        setupGroup();
        addCrmwPool(200L, "[]");
        addCrmwPool(201L, "[]");
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // K凭证的临时标的当前状态
        addCrmwState("001.IB", "K.IB", 200L);
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 两个凭证分别持有原正式标的组
            addCrmwState(code, "K.IB", 201L);
            addCrmwState(code, "L.IB", 201L);
        }
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(200,'in_mutex',201,0)");
        // 当前状态转正和互斥调出统一即时生效
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE crmw_scode='K.IB'"
                + " AND target_pool_id=200 AND is_deleted=0")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE crmw_scode='K.IB'"
                + " AND target_pool_id=201 AND is_deleted=0")).isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE crmw_scode='L.IB' AND is_deleted=0")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjuster_id='0' AND audit_status='20'"))
                .isEqualTo(7);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw")).isEqualTo(6);
    }

    /** 建立 CRMW 临时标的调入申请与同券正式标的的互斥原持仓。 */
    private TempSecurityCodeDto prepareCrmwMutexInbound() throws Exception {
        // 正式组与两个 CRMW 池
        setupGroup();
        addCrmwPool(200L, "[]");
        addCrmwPool(201L, "[]");
        jdbc.update("UPDATE ip_investment_pool SET pool_name='CRMW目标池' WHERE id=200");
        jdbc.update("UPDATE ip_investment_pool SET pool_name='CRMW原池' WHERE id=201");
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // K和L凭证持仓分别处理，L不能被连带调出
            addCrmwState(code, "K.IB", 201L);
            addCrmwState(code, "L.IB", 201L);
        }
        // 原审批只包含T调入，转正前还未命中正式标的所在互斥池
        IpAdjustLogBo original = baseLog("001.IB", 200L);
        original.setPoolType("crmw");
        original.setCrmwScode("K.IB");
        original.setCrmwStype("crmw");
        original.setAuditStatus("00");
        original.setAdjustBatchNo("CRMW_MUTEX");
        original.setFlowId(23L);
        adjustMapper.addAdjustLog(original);
        AdjustSecuritySnapshotCrmwBo snapshot = new AdjustSecuritySnapshotCrmwBo();
        snapshot.setAdjustLogId(original.getId());
        snapshot.setWindCode("001.IB");
        snapshot.setCrmwScode("K.IB");
        snapshot.setCrmwStype("crmw");
        snapshot.setAnalysis("互斥转池原分析");
        snapshot.setIsDeleted(0);
        crmwMapper.addAdjustSecuritySnapshotCrmw(snapshot);
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(200,'in_mutex',201,0)");
        return temp;
    }

    /** 验证原批次整组互斥转池，保留发起人、业务快照、审批状态和已完成步骤。 */
    private void assertPendingMutexTransfer(String status, boolean approve) throws Exception {
        // P代表五级库，Q代表一级库
        setupGroup();
        addPool(100L, "[]", 10);
        addPool(101L, "[]", 10);
        jdbc.update("UPDATE ip_investment_pool SET pool_name='五级库' WHERE id=100");
        jdbc.update("UPDATE ip_investment_pool SET pool_name='一级库' WHERE id=101");
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        for (String code : Arrays.asList("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 正式组已经在一级库
            addActive(code, 101L);
        }
        // T只在调入五级库的流程中，不存在T的当前持仓
        IpAdjustLogBo original = addPending("001.IB", 100L, "调入", status);
        AdjustSecuritySnapshotBo snapshot = new AdjustSecuritySnapshotBo();
        snapshot.setAdjustLogId(original.getId());
        snapshot.setWindCode("001.IB");
        snapshot.setAnalysis("转池原申请分析");
        snapshot.setIsDeleted(0);
        adjustMapper.addAdjustSecuritySnapshot(snapshot);
        jdbc.update("INSERT INTO ip_adjust_step(adjust_log_id,adjust_batch_no,step_status) VALUES(?,'ORIGINAL','20')",
                original.getId());
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(100,'in_mutex',101,0)");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(101,'in_mutex',100,0)");
        // 转正后原审批批次应包含3个市场调入和3个市场互斥调出
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        List<IpAdjustLogBo> logs = adjustMapper.queryAdjustLogListForAudit(original.getId(), "ORIGINAL");
        assertThat(logs).hasSize(6);
        assertThat(logs).extracting("adjusterId").containsOnly("1");
        assertThat(logs).extracting("flowId").containsOnly(9L);
        assertThat(logs).extracting("auditStatus").containsOnly(status);
        assertThat(jdbc.queryForList("SELECT adjust_reason FROM ip_adjust_log WHERE adjust_batch_no='ORIGINAL'"
                + " AND adjust_mode='调出'", String.class))
                .hasSize(3).allSatisfy(reason -> assertThat(reason)
                        .contains("调入池：五级库", "互斥配套调出池：一级库")
                        .doesNotContain("调入池：100", "互斥配套调出池：101"));
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='ORIGINAL'"
                + " AND target_pool_id=101 AND adjust_mode='调出'")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot WHERE analysis='转池原申请分析'"))
                .isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_step WHERE step_status='20'")).isEqualTo(1);
        assertThat(activeCodes(100L)).isEmpty();
        assertThat(activeCodes(101L)).hasSize(3);
        if (approve) {
            // 复用原审批完成时的批次更新与池状态落地入口，核对新增调出能随原批次生效
            adjustMapper.editActiveAdjustLogAuditStatus(original.getId(), "ORIGINAL", "20");
            SecurityPoolAdjustService poolService = new SecurityPoolAdjustService();
            ReflectionTestUtils.setField(poolService, "securityPoolAdjustMapper", adjustMapper);
            poolService.applyPoolStatusChanges(logs);
            assertThat(activeCodes(100L)).hasSize(3);
            assertThat(activeCodes(101L)).isEmpty();
        }
    }

    /** 历史已通过但不在池的记录保留原临时代码。 */
    @Test
    public void completedHistoryShouldRemainUnchanged() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 构造原业务日志的公共字段
        IpAdjustLogBo history = baseLog("001.IB", 100L);
        history.setAuditStatus("20");
        adjustMapper.addAdjustLog(history);
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=?", String.class, history.getId()))
                .isEqualTo("001.IB");
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(1);
    }

    /** 原同批转池申请仍可使用待调出池作为来源池。 */
    @Test
    public void pendingTransferShouldUseOriginalSourceAndSharedBatch() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 配置本场景的普通目标池及容量
        addPool(101L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立转换前真实的在池状态
        addActive("001.IB", 100L);
        // 建立需要保留审批进度的原申请
        addPending("001.IB", 100L, "调出", "00");
        // 建立需要保留审批进度的原申请
        IpAdjustLogBo inbound = addPending("001.IB", 101L, "调入", "00");
        jdbc.update("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(101,'source',100,0)");
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(100L)).hasSize(3);
        // 核对数据库中的实际在池代码集合
        assertThat(activeCodes(101L)).isEmpty();
        assertThat(adjustMapper.queryAdjustLogListForAudit(inbound.getId(), "ORIGINAL")).hasSize(6);
    }

    /** CRMW 临时标的退出，同一凭证的全部正式市场标的统一进入，调整人使用系统。 */
    @Test
    public void crmwPoolShouldConvertWholeGroupWithSystemAdjuster() throws Exception {
        // 建立同券三个市场的正式主数据和关联关系
        setupGroup();
        // 配置 CRMW 目标池，允许全部标的市场
        addCrmwPool(200L, "[]");
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        jdbc.update("INSERT INTO ip_pool_status_crmw(security_code,security_short_name,security_type,crmw_scode,crmw_mktcode,crmw_stype,"
                + "target_pool_id,target_pool_name,pool_type,adjust_type,audit_status,is_deleted)"
                + " VALUES('001.IB','临时债','mtn','K.IB','NIB','crmw',200,'CRMW池','crmw','手工调整','20',0)");
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(jdbc.queryForList("SELECT security_code FROM ip_pool_status_crmw WHERE is_deleted=0", String.class))
                .containsExactlyInAnyOrder("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ");
        assertThat(jdbc.queryForList("SELECT crmw_scode FROM ip_pool_status_crmw WHERE is_deleted=0", String.class))
                .containsOnly("K.IB");
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status")).isZero();
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjuster_id='0' AND adjuster_name='系统'"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0 AND adjuster_id='0' AND adjuster_name='系统'"))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw")).isEqualTo(3);
    }

    /** 普通池及 CRMW 同时整组迁移，原审批保留发起人，即时记录统一使用系统调整人。 */
    @Test
    public void mixedOrdinaryAndCrmwBusinessShouldConvertInSeparateStages() throws Exception {
        // 建立普通池整组在池及原调出申请
        setupGroup();
        // 配置本次普通目标池
        addPool(100L, "[]", 10);
        // 登记待转正的临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立普通池当前状态
        addActive("001.IB", 100L);
        // 保留普通池原调出流程
        IpAdjustLogBo ordinaryPending = addPending("001.IB", 100L, "调出", "00");
        // 建立同一临时债作为 CRMW 标的的当前状态和原调出申请
        IpAdjustLogBo crmwPending = addCrmwActiveAndPending();

        // 从统一入口依次校验、转换普通池和 CRMW 业务
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));

        // 普通池三市场当前状态、原批次和快照均完整迁移
        assertThat(activeCodes(100L)).containsExactly("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ");
        assertThat(adjustMapper.queryAdjustLogListForAudit(ordinaryPending.getId(), "ORIGINAL")).hasSize(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='ORIGINAL' AND audit_status='00'"))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot")).isEqualTo(6);
        // CRMW 原申请只换标的，保留凭证、批次、状态和流程
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=?", String.class, crmwPending.getId()))
                .isEqualTo("FORMAL.IB");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='CRMW_ORIGINAL'"
                + " AND crmw_scode='K.IB' AND audit_status='00' AND flow_id=23 AND adjuster_id='1'")).isEqualTo(3);
        // CRMW 当前状态包含全部市场代码，保留原凭证
        assertThat(jdbc.queryForList("SELECT security_code FROM ip_pool_status_crmw WHERE is_deleted=0", String.class))
                .containsExactlyInAnyOrder("FORMAL.IB", "FORMAL.SH", "FORMAL.SZ");
        assertThat(jdbc.queryForList("SELECT crmw_scode FROM ip_pool_status_crmw WHERE is_deleted=0", String.class))
                .containsOnly("K.IB");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE pool_type='crmw' AND audit_status='20'"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE audit_status='20' AND adjust_batch_no<>'HISTORY'"
                + " AND adjuster_id='0' AND adjuster_name='系统'")).isEqualTo(8);
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=0 AND adjuster_id='0' AND adjuster_name='系统'"))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_step")).isZero();
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("updated");
    }

    /** CRMW 阶段失败时，之前的普通池迁移、原申请换码和转正登记均回滚。 */
    @Test
    public void crmwStageFailureShouldRollbackEarlierOrdinaryConversion() throws Exception {
        // 建立普通池和同券三个正式市场
        setupGroup();
        // 配置普通目标池
        addPool(100L, "[]", 10);
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立普通池当前状态
        addActive("001.IB", 100L);
        // 建立普通池待调出申请
        IpAdjustLogBo ordinaryPending = addPending("001.IB", 100L, "调出", "00");
        // 建立 CRMW 当前状态及待调出申请
        IpAdjustLogBo crmwPending = addCrmwActiveAndPending();
        AdjustSecuritySnapshotCrmwBo originalSnapshot = new AdjustSecuritySnapshotCrmwBo();
        originalSnapshot.setAdjustLogId(crmwPending.getId());
        originalSnapshot.setWindCode("001.IB");
        originalSnapshot.setCrmwScode("K.IB");
        originalSnapshot.setCrmwStype("crmw");
        originalSnapshot.setAnalysis("原CRMW申请分析");
        originalSnapshot.setIsDeleted(0);
        crmwMapper.addAdjustSecuritySnapshotCrmw(originalSnapshot);
        jdbc.execute("ALTER TABLE ip_adjust_security_snapshot_crmw ADD CONSTRAINT fail_crmw_conversion"
                + " CHECK (analysis IS NULL OR wind_code <> 'FORMAL.SZ')");

        // 两类当前状态迁移完成后，在 CRMW 流程中最后一个关联市场的快照写入时失败
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .isInstanceOf(RuntimeException.class);

        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
        // 普通池状态和在途主项恢复为原临时码
        assertThat(activeCodes(100L)).containsExactly("001.IB");
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=?", String.class, ordinaryPending.getId()))
                .isEqualTo("001.IB");
        // CRMW 已换码的在途申请和当前状态同样回滚
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=?", String.class, crmwPending.getId()))
                .isEqualTo("001.IB");
        assertThat(jdbc.queryForList("SELECT security_code FROM ip_pool_status_crmw WHERE is_deleted=0", String.class))
                .containsExactly("001.IB");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot")).isZero();
        assertThat(count("SELECT COUNT(*) FROM rrs_temp_security_code_update_log")).isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT wind_code FROM ip_adjust_security_snapshot_crmw WHERE id=?",
                String.class, originalSnapshot.getId())).isEqualTo("001.IB");
        assertThat(jdbc.queryForObject("SELECT security_status FROM rrs_securityinfo WHERE wind_code='001.IB'", String.class))
                .isEqualTo("L");
    }

    /** CRMW 调入流程中的原主项和两个关联项沿用原审批，不提前落池。 */
    @Test
    public void crmwPendingInboundShouldExpandWholeGroup() throws Exception {
        // 验证审批中的原申请及业务快照
        assertCrmwPendingInbound("00");
    }

    /** CRMW 驳回待修改申请也保留原状态并补齐多市场。 */
    @Test
    public void crmwEditableInboundShouldKeepStatusAndSnapshots() throws Exception {
        // 验证待修改申请，不能被转正自动审批
        assertCrmwPendingInbound("11");
    }

    /** 同凭证正式组已全部在池时，仅移除临时标的，不重复调入。 */
    @Test
    public void crmwExistingWholeGroupShouldOnlyRemoveTemporaryState() throws Exception {
        // 准备同券三市场及 CRMW 池
        setupGroup();
        // 配置 CRMW 目标池
        addCrmwPool(200L, "[]");
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        for (String code : Arrays.asList("001.IB", "FORMAL.IB", "FORMAL.SH", "FORMAL.SZ")) {
            // 同一凭证下同时存在临时标的和完整正式组
            addCrmwState(code, "K.IB", 200L);
        }
        // 转正仅清理临时标的
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_mode='调出' AND adjuster_id='0'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_mode='调入'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0 AND adjuster_id='1'"))
                .isEqualTo(3);
    }

    /** 其他凭证已有正式代码，不得误判本凭证的正式组已经在池。 */
    @Test
    public void crmwOtherCredentialShouldNotSuppressFormalGroupEntry() throws Exception {
        // 准备正式组
        setupGroup();
        // 配置同一 CRMW 池
        addCrmwPool(200L, "[]");
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 本次凭证绑定临时标的
        addCrmwState("001.IB", "K.IB", 200L);
        // 无关凭证仅绑定其中一个正式市场，不应阻断或自动补修
        addCrmwState("FORMAL.IB", "OTHER.IB", 200L);
        // 转正本凭证
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0 AND crmw_scode='K.IB'"))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0 AND crmw_scode='OTHER.IB'"))
                .isEqualTo(1);
    }

    /** 同一临时标的属于两个凭证时，各凭证分别形成完整正式组。 */
    @Test
    public void crmwMultipleCredentialsShouldConvertIndependently() throws Exception {
        // 准备正式组
        setupGroup();
        // 配置共享目标池
        addCrmwPool(200L, "[]");
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 两个凭证均绑定临时标的
        addCrmwState("001.IB", "K.IB", 200L);
        // 第二个凭证必须独立生成三条正式记录
        addCrmwState("001.IB", "OTHER.IB", 200L);
        // 同次转正统一处理两个凭证
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjuster_id='0' AND adjuster_name='系统'"))
                .isEqualTo(8);
        assertThat(count("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw")).isEqualTo(6);
    }

    /** 同凭证仅部分正式市场在池属于数据异常，整次转正不写入。 */
    @Test
    public void crmwPartialFormalGroupShouldRejectWithoutWrites() throws Exception {
        // 准备正式组
        setupGroup();
        // 配置目标池
        addCrmwPool(200L, "[]");
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 准备临时在池记录
        addCrmwState("001.IB", "K.IB", 200L);
        // 制造同凭证正式组部分在池的数据异常
        addCrmwState("FORMAL.IB", "K.IB", 200L);
        // 校验阶段拒绝本次转换
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("未整组在池");
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0")).isEqualTo(2);
    }

    /** CRMW 新进入的正式市场必须全部符合目标池配置。 */
    @Test
    public void crmwUnsupportedMemberMarketShouldRejectWholeMapping() throws Exception {
        // 准备三市场
        setupGroup();
        // 目标池只允许银行间市场
        addCrmwPool(200L, "[\"NIB\"]");
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 原临时标的已在池
        addCrmwState("001.IB", "K.IB", 200L);
        // 另外两个市场不符时整组拒绝
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("市场不符合目标池");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isZero();
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
    }

    /** 同凭证同目标池的正式市场已有流程中数据时，不合并原审批。 */
    @Test
    public void crmwFormalPendingConflictShouldRejectConversion() throws Exception {
        // 准备正式组和临时标的原业务
        setupGroup();
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立本次凭证的原在池和原申请
        IpAdjustLogBo pending = addCrmwActiveAndPending();
        // 为另一正式市场构造独立流程
        IpAdjustLogBo conflict = baseLog("FORMAL.SH", 200L);
        conflict.setCrmwScode("K.IB");
        conflict.setCrmwStype("crmw");
        conflict.setPoolType("crmw");
        conflict.setAuditStatus("00");
        conflict.setAdjustBatchNo("INDEPENDENT");
        adjustMapper.addAdjustLog(conflict);
        // 转正不能自动合并独立审批
        assertThatThrownBy(() -> service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB")))
                .hasMessageContaining("CRMW库存在流程中数据").hasMessageContaining("FORMAL.SH");
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=?", String.class, pending.getId()))
                .isEqualTo("001.IB");
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getStatus()).isEqualTo("temporary");
    }

    /** CRMW 多池逐池整组转换，同次即时记录共用批次。 */
    @Test
    public void crmwMultiplePoolsShouldConvertWholeGroupsInOneBatch() throws Exception {
        // 准备正式组
        setupGroup();
        // 配置第一个池
        addCrmwPool(200L, "[]");
        // 配置第二个池
        addCrmwPool(201L, "[]");
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 同凭证临时标的同时在两个池
        addCrmwState("001.IB", "K.IB", 200L);
        // 第二池原状态
        addCrmwState("001.IB", "K.IB", 201L);
        // 逐池转换为正式组
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(8);
        assertThat(count("SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log")).isEqualTo(1);
    }

    /** 定时任务同样扩展 CRMW 标的全部市场，系统调整人和 job 来源正确。 */
    @Test
    public void crmwJobShouldConvertWholeGroupWithSystemAdjuster() throws Exception {
        // 准备正式组
        setupGroup();
        // 配置 CRMW 池
        addCrmwPool(200L, "[]");
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立临时标的在池状态
        addCrmwState("001.IB", "K.IB", 200L);
        jdbc.update("UPDATE rrs_temp_security_code SET security_code='FORMAL.IB' WHERE id=?", temp.getId());
        service.editTempSecurityCodeToUpdatedByJob(temp.getId());
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjuster_id='0' AND adjuster_name='系统' AND adjust_advice IS NULL"))
                .isEqualTo(4);
        assertThat(mapper.queryTempSecurityCodeById(temp.getId()).getOprtSource()).isEqualTo("job");
    }

    /** 单市场 CRMW 标的仍生成一条临时调出和一条正式调入。 */
    @Test
    public void crmwSingleMarketShouldStillConvertOneFormalCode() throws Exception {
        // 仅保留默认的银行间正式证券，不建立其他市场
        addCrmwPool(200L, "[]");
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 原临时标的在池
        addCrmwState("001.IB", "K.IB", 200L);
        // 单市场转正
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw WHERE is_deleted=0")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw")).isEqualTo(1);
    }

    /** 检查 CRMW 两种流程中状态的同批申请、原发起人和业务快照。 */
    private void assertCrmwPendingInbound(String status) throws Exception {
        // 准备正式组
        setupGroup();
        // 配置目标池
        addCrmwPool(200L, "[]");
        // 登记临时债
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 构造原申请
        IpAdjustLogBo original = baseLog("001.IB", 200L);
        original.setPoolType("crmw");
        original.setCrmwScode("K.IB");
        original.setCrmwStype("crmw");
        original.setAuditStatus(status);
        original.setAdjustBatchNo("CRMW_PENDING");
        original.setFlowId(23L);
        adjustMapper.addAdjustLog(original);
        AdjustSecuritySnapshotCrmwBo snapshot = new AdjustSecuritySnapshotCrmwBo();
        snapshot.setAdjustLogId(original.getId());
        snapshot.setWindCode("001.IB");
        snapshot.setCrmwScode("K.IB");
        snapshot.setCrmwStype("crmw");
        snapshot.setAnalysis("原CRMW申请分析");
        snapshot.setIsDeleted(0);
        crmwMapper.addAdjustSecuritySnapshotCrmw(snapshot);
        // 转正补其他市场，原审批不变
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status_crmw")).isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='CRMW_PENDING' AND flow_id=23"
                + " AND adjuster_id='1' AND audit_status='" + status + "'")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw WHERE analysis='原CRMW申请分析'"
                + " AND crmw_scode='K.IB' AND wind_code LIKE 'FORMAL.%'")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT wind_code FROM ip_adjust_security_snapshot_crmw WHERE id=?", String.class, snapshot.getId()))
                .isEqualTo("FORMAL.IB");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_step")).isZero();
    }

    /** 建立指定凭证和实际标的在 CRMW 池的当前状态。 */
    private void addCrmwState(String code, String credential, Long poolId) {
        jdbc.update("INSERT INTO ip_pool_status_crmw(security_code,security_short_name,security_type,crmw_scode,crmw_mktcode,crmw_stype"
                + ",target_pool_id,target_pool_name,pool_type,adjust_type,audit_status,adjuster_id,adjuster_name,is_deleted)"
                + " VALUES(?,?,'mtn',?,'NIB','crmw',?,'CRMW池','crmw','手工调整','20','1','原申请人',0)",
                code, code, credential, poolId);
    }

    /** 建立凭证 K.IB 与临时标的的 CRMW 当前记录及原审批申请。 */
    private IpAdjustLogBo addCrmwActiveAndPending() {
        // 配置本次 CRMW 目标池
        addCrmwPool(200L, "[]");
        jdbc.update("INSERT INTO ip_pool_status_crmw(security_code,security_short_name,security_type,crmw_scode,crmw_mktcode,crmw_stype,"
                + "target_pool_id,target_pool_name,pool_type,adjust_type,audit_status,is_deleted)"
                + " VALUES('001.IB','临时债','mtn','K.IB','NIB','crmw',200,'CRMW池','crmw','手工调整','20',0)");
        // 复用日志公共字段，使用独立的 CRMW 原批次和流程
        IpAdjustLogBo pending = baseLog("001.IB", 200L);
        pending.setPoolType("crmw");
        pending.setTargetPoolName("CRMW池");
        pending.setAdjustMode("调出");
        pending.setAuditStatus("00");
        pending.setAdjustBatchNo("CRMW_ORIGINAL");
        pending.setFlowId(23L);
        pending.setCrmwScode("K.IB");
        pending.setCrmwMktcode("NIB");
        pending.setCrmwStype("crmw");
        adjustMapper.addAdjustLog(pending);
        return pending;
    }

    /** 创建 CRMW 池，不重新配置容量、报告或冻结期等调库准入条件。 */
    private void addCrmwPool(Long id, String markets) {
        jdbc.update("INSERT INTO ip_investment_pool(id,pool_name,pool_type,status,market_codes,is_deleted)"
                + " VALUES (?,'CRMW池','crmw','enabled',? FORMAT JSON,0)", id, markets);
    }

    /** 核对两类在途状态均逐码保存快照并沿用原批次。 */
    private void assertPendingInbound(String status) throws Exception {
        // 建立尚未落池的原申请及用户已填写的业务快照
        setupGroup();
        // 配置本场景的普通目标池及容量
        addPool(100L, "[]", 10);
        // 通过新增入口登记本场景临时代码
        TempSecurityCodeDto temp = service.addTempSecurityCode(addReq("001.IB"));
        // 建立需要保留审批进度的原申请
        IpAdjustLogBo original = addPending("001.IB", 100L, "调入", status);
        AdjustSecuritySnapshotBo snapshot = new AdjustSecuritySnapshotBo();
        snapshot.setAdjustLogId(original.getId());
        snapshot.setWindCode("001.IB");
        snapshot.setAnalysis("原申请填写的分析");
        snapshot.setIsDeleted(0);
        adjustMapper.addAdjustSecuritySnapshot(snapshot);
        // 通过转正入口提交选中的正式代码
        service.editTempSecurityCodeToUpdated(operation(temp.getId(), "FORMAL.IB"));
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_pool_status")).isZero();
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_step")).isZero();
        assertThat(adjustMapper.queryAdjustLogListForAudit(original.getId(), "ORIGINAL")).hasSize(3);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE adjust_batch_no='ORIGINAL' AND audit_status='" + status + "'"))
                .isEqualTo(3);
        // 核对实际写入数量，避免重复记账或部分落地
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot WHERE analysis='原申请填写的分析' AND wind_code LIKE 'FORMAL.%'"))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT wind_code FROM ip_adjust_security_snapshot WHERE id=?", String.class, snapshot.getId()))
                .isEqualTo("FORMAL.IB");
    }

    /** 建立三条独立证券主档及同券查询结果。 */
    private void setupGroup() {
        for (String suffix : Arrays.asList("SH", "SZ")) {
            jdbc.update("INSERT INTO rrs_securityinfo(wind_code,short_name,full_name,security_type,security_status,security_source)"
                    + " VALUES (?,?,'正式全称','mtn','L','official')", "FORMAL." + suffix, "正式" + suffix);
        }
        SecurityMarketDto sh = new SecurityMarketDto();
        sh.setWindCode("FORMAL.SH");
        sh.setMarketCode("SSE");
        SecurityMarketDto sz = new SecurityMarketDto();
        sz.setWindCode("FORMAL.SZ");
        sz.setMarketCode("SZSE");
        when(markets.queryRelatedMarketCodes("FORMAL.IB")).thenReturn(Arrays.asList(sh, sz));
        when(markets.queryMarketCode("FORMAL.SH")).thenReturn("SSE");
        when(markets.queryMarketCode("FORMAL.SZ")).thenReturn("SZSE");
    }

    /** 建立目标普通池，不配置报告和冻结期等新增申请条件。 */
    private void addPool(Long id, String marketCodes, int capacity) {
        jdbc.update("INSERT INTO ip_investment_pool(id,pool_name,pool_type,status,market_codes,max_capacity,is_deleted)"
                + " VALUES (?,'测试普通池','credit_bond','enabled',? FORMAT JSON,?,0)", id, marketCodes, capacity);
    }

    /** 建立原在池日志和真实池状态。 */
    private void addActive(String code, Long poolId) {
        // 复用实际 Mapper 建立已生效记录
        IpAdjustLogBo log = baseLog(code, poolId);
        log.setAuditStatus("20");
        log.setAdjustBatchNo("HISTORY");
        log.setFlowId(17L);
        adjustMapper.addAdjustLog(log);
        log.setAdjustLogId(log.getId());
        adjustMapper.addPoolStatus(log);
    }

    /** 建立原在途记录，不创建独立的新流程。 */
    private IpAdjustLogBo addPending(String code, Long poolId, String mode, String status) {
        // 沿用统一申请身份字段
        IpAdjustLogBo log = baseLog(code, poolId);
        log.setAuditStatus(status);
        log.setAdjustMode(mode);
        log.setAdjustBatchNo("ORIGINAL");
        log.setFlowId(9L);
        log.setFlowKey("originalFlow");
        adjustMapper.addAdjustLog(log);
        return log;
    }

    /** 构建可真实插入的日志公共字段。 */
    private IpAdjustLogBo baseLog(String code, Long poolId) {
        IpAdjustLogBo log = new IpAdjustLogBo();
        log.setSecurityCode(code);
        log.setSecurityShortName(code);
        log.setSecurityType("mtn");
        log.setAdjustType("手工调整");
        log.setAdjustMode("调入");
        log.setTargetPoolId(poolId);
        log.setTargetPoolName("测试普通池");
        log.setPoolType("credit_bond");
        log.setAdjusterId("1");
        log.setAdjusterName("测试人员");
        return log;
    }

    /** 查询某池当前有效代码，验证不存在部分市场在池。 */
    private List<String> activeCodes(Long poolId) {
        return jdbc.queryForList("SELECT security_code FROM ip_pool_status WHERE target_pool_id=?"
                + " AND audit_status='20' AND is_deleted=0 ORDER BY security_code", String.class, poolId);
    }

    /** 查询隔离测试库中的实际记录数。 */
    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    /** 从生产 Demo 提取指定表的完整 INSERT，保持字段和值的真实对应关系。 */
    private void executeDemoInsert(String file, String table) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
        Matcher insert = Pattern.compile("INSERT INTO `" + table + "`\\s*\\(.*?;", Pattern.DOTALL).matcher(sql);
        assertThat(insert.find()).as("Demo INSERT %s", table).isTrue();
        jdbc.execute(insert.group());
    }

    /** 验证转正后两类主档的 ID 及临时禁用状态。 */
    private void assertMasterIds() {
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE temp_security_code='001.IB'", String.class))
                .isEqualTo("TEMP_001.IB");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='001.IB'", String.class))
                .isEqualTo("TEMP_001.IB");
        assertThat(jdbc.queryForObject("SELECT security_status FROM rrs_securityinfo WHERE wind_code='001.IB'", String.class))
                .isEqualTo("D");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_securityinfo WHERE wind_code='FORMAL.IB'", String.class))
                .isEqualTo("OFFICIAL_ID");
    }

    /** 提取原始建表列定义，仅移除 H2 不支持的 MySQL 表选项。 */
    private void createSchema(String file) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("CREATE TABLE\\s+`?\\w+`?\\s*\\(.*?\\)\\s*ENGINE\\s*=.*?;", Pattern.DOTALL)
                .matcher(sql);
        int count = 0;
        while (matcher.find()) {
            jdbc.execute(matcher.group().replaceFirst("(?s)\\)\\s*ENGINE.*$", ")"));
            count++;
        }
        assertThat(count).as("生产表结构 %s", file).isPositive();
    }

    /** 构造具有完整业务必填项的新增请求。 */
    private TempSecurityCodeReq addReq(String code) throws Exception {
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setTempSecurityCode(code);
        req.setTempSecurityName("测试临时证券");
        req.setTempSecurityMarket("NIB");
        req.setTempSecurityType("mtn");
        req.setTempCompanyCode("C10001");
        SimpleDateFormat dates = new SimpleDateFormat("yyyy-MM-dd");
        req.setTempIssueDate(dates.parse("2026-01-01"));
        req.setTempMaturityDate(dates.parse("2031-01-01"));
        return req;
    }

    /** 构造状态操作请求，临时字段及 ID 不由客户端传入。 */
    private TempSecurityCodeReq operation(Long id, String formalCode) {
        TempSecurityCodeReq req = new TempSecurityCodeReq();
        req.setId(id);
        req.setSecurityCode(formalCode);
        return req;
    }

    /** 为内存库提供生产 Mapper 使用的 MySQL 紧凑日期函数。 */
    public static String formatDate(Timestamp value, String pattern) {
        if (!"%Y%m%d".equals(pattern)) {
            throw new IllegalArgumentException("测试未支持的日期格式：" + pattern);
        }
        return value == null ? null : new SimpleDateFormat("yyyyMMdd").format(value);
    }
}
