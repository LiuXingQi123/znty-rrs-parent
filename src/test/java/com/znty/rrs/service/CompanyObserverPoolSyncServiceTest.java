package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.CompanyObserverDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.CompanyObserverMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 主体观察名单任务的双向同步、幂等和错误处理测试。 */
public class CompanyObserverPoolSyncServiceTest {
    /** 配置可使用其他普通池，读取和写入范围均不混入旧的默认池，联动仍然生效。 */
    @Test
    public void shouldUseConfiguredPoolIdsAndRelationsOnly() {
        Fixture f = new Fixture();
        f.task.setParamJson("{\"observePoolId\":116,\"keyObservePoolId\":123}");
        when(f.poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(116L, "other"),
                pool(123L, "other"), pool(117L, "blacklist")));
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                relation(123L, 117L, "in_linked"), relation(123L, 117L, "out_linked")));
        f.observers.add(observer("C1", "2"));
        f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
        f.states.put("C1", new LinkedHashSet<>(Arrays.asList(16L, 23L)));
        f.states.put("B1", new LinkedHashSet<>(Arrays.asList(16L, 23L)));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(1);
        verify(f.sourceMapper).queryCurrentCompanyList(Arrays.asList(116L, 123L));
        f.observers.get(0).setObserverType("3");
        ScheduledTaskResult switched = f.service.execute();
        assertThat(switched.isSuccess()).isTrue();
        assertThat(switched.getAffectedCount()).isEqualTo(3);
        assertThat(switched.getDetailLog()).contains("2=116", "3=123", "源池=池123", "配置池=[117]");
        assertThat(f.states.get("C1")).containsExactly(16L, 23L, 123L, 117L);
        assertThat(f.states.get("B1")).containsExactly(16L, 23L);
        assertThat(f.logs).allSatisfy(row -> assertThat(row.getTargetPoolId()).isIn(116L, 123L, 117L));
        f.observers.clear();
        f.currentCompanies.add(item("C1", "company"));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(2);
        assertThat(f.states.get("C1")).containsExactly(16L, 23L);
        assertThat(f.states.get("B1")).containsExactly(16L, 23L);
    }

    /** 只配置一类时，仅扫描和管理该目标池，不隐式补入另一默认池。 */
    @Test
    public void shouldManageOnlyExplicitlyConfiguredType() {
        for (String paramJson : Arrays.asList("{\"observePoolId\":16}",
                "{\"observePoolId\":16,\"keyObservePoolId\":null}")) {
            Fixture f = new Fixture();
            f.task.setParamJson(paramJson);
            f.observers.add(observer("C1", "3"));
            f.states.put("C1", new LinkedHashSet<>(Arrays.asList(16L, 23L)));
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAffectedCount()).isEqualTo(1);
            assertThat(f.states.get("C1")).containsExactly(23L);
            verify(f.sourceMapper).queryCurrentCompanyList(Collections.singletonList(16L));
        }
    }

    /** 每轮从库表读取最新参数，移除配置后不再管理之前的目标池。 */
    @Test
    public void shouldReloadExtensionParametersOnEachExecution() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "2"));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(1);
        f.task.setParamJson("{\"observePoolId\":99}");
        when(f.poolMapper.queryPoolList()).thenReturn(Collections.singletonList(pool(99L, "other")));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(1);
        assertThat(f.states.get("C1")).containsExactly(16L, 99L);
        verify(f.sourceMapper).queryCurrentCompanyList(Collections.singletonList(99L));
    }

    /** 空参数、拼写错误、非法数字及冲突映射在任何业务查询和写入之前失败。 */
    @Test
    public void shouldRejectInvalidParametersBeforeScanningOrWriting() {
        List<String> invalid = Arrays.asList(null, "", "{}", "[]", "null", "{", "{\"poolIds\":[16,23]}",
                "{\"observePoolId\":null}", "{\"observePoolId\":\"16\"}", "{\"observePoolId\":true}",
                "{\"observePoolId\":0}", "{\"observePoolId\":-1}", "{\"observePoolId\":16.5}",
                "{\"observePoolId\":9223372036854775808}", "{\"observePoolId\":16,\"keyObservePoolId\":16}");
        for (String paramJson : invalid) {
            Fixture f = new Fixture();
            f.task.setParamJson(paramJson);
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).as("非法参数：%s", paramJson).isFalse();
            assertThat(f.logs).isEmpty();
            verifyNoInteractions(f.sourceMapper, f.poolMapper, f.statusMapper, f.bondMapper);
        }
        Fixture missingTask = new Fixture();
        when(missingTask.taskMapper.queryTaskByCode(CompanyObserverPoolSyncService.TASK_CODE)).thenReturn(null);
        assertThat(missingTask.service.execute().isSuccess()).isFalse();
        verifyNoInteractions(missingTask.sourceMapper, missingTask.poolMapper, missingTask.statusMapper, missingTask.bondMapper);
    }

    /** 配置的池不存在、停用、已删除或是组合池时，禁止扫描名单及写入。 */
    @Test
    public void shouldValidateConfiguredTargetPoolsBeforeWriting() {
        InvestmentPoolBo disabled = pool(99L, "other");
        disabled.setStatus("disabled");
        InvestmentPoolBo deleted = pool(99L, "other");
        deleted.setIsDeleted(1);
        for (InvestmentPoolBo target : Arrays.asList(pool(98L, "other"), disabled, deleted, pool(99L, "crmw"))) {
            Fixture f = new Fixture();
            f.task.setParamJson("{\"keyObservePoolId\":99}");
            when(f.poolMapper.queryPoolList()).thenReturn(Collections.singletonList(target));
            assertThat(f.service.execute().isSuccess()).isFalse();
            verifyNoInteractions(f.sourceMapper, f.statusMapper, f.bondMapper);
        }
    }

    /** 类型从观察切换到重点观察时，先退出主体及债券旧池，再进入新池，重复执行不重复记账。 */
    @Test
    public void shouldMoveBothDirectionsBeforeInboundAndRemainIdempotent() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        f.bonds.put("C1", Collections.singletonList(item("B1", "company_bond")));
        f.states.put("C1", new LinkedHashSet<>(Collections.singletonList(16L)));
        f.states.put("B1", new LinkedHashSet<>(Collections.singletonList(16L)));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(2);
        assertThat(f.logs).extracting(IpAdjustLogBo::getAdjustMode).containsExactly("调出", "调入");
        assertThat(f.states.get("C1")).containsExactly(23L);
        assertThat(f.states.get("B1")).containsExactly(16L);
        ScheduledTaskResult repeated = f.service.execute();
        assertThat(repeated.isSuccess()).isTrue();
        assertThat(repeated.getAffectedCount()).isZero();
        assertThat(f.logs).hasSize(2);
    }

    /** 在池记录发行主体编码为空时只跳过该记录，本轮继续执行并在过程日志和结果中记录。 */
    @Test
    public void shouldSkipInPoolRowWithMissingIssuerCode() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "2"));
        // 池内债券的发行主体编码缺失，应跳过而不是中断整轮任务
        ScheduledAdjustCandidateDto missingIssuer = item(null, "company");
        missingIssuer.setSecurityShortName("某某债");
        f.currentCompanies.add(missingIssuer);
        ScheduledTaskResult result = f.service.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("跳过发行主体编码为空记录1条（不阻断本轮）");
        assertThat(result.getDetailLog()).contains("WARN", "跳过发行主体编码为空的在池记录 1 条（示例：某某债）",
                "跳过发行主体编码为空记录 1 条");
        // 主体 C1 正常调入，缺失编码的记录不产生任何写入
        assertThat(f.states.get("C1")).containsExactly(16L);
        assertThat(f.logs).extracting(IpAdjustLogBo::getSecurityCode).containsExactly("C1");
    }

    /** 观察类型变化只同步主体与非 ABS 债券，旗下 ABS 债保持原池状态不动。 */
    @Test
    public void shouldLeaveAllBondsOutOfObserverSync() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        f.bonds.put("C1", Arrays.asList(item("ABS1", "abs"), item("B1", "mtn")));
        f.states.put("C1", new LinkedHashSet<>(Collections.singletonList(16L)));
        f.states.put("ABS1", new LinkedHashSet<>(Collections.singletonList(16L)));
        f.states.put("B1", new LinkedHashSet<>(Collections.singletonList(16L)));
        ScheduledTaskResult result = f.service.execute();
        assertThat(result.isSuccess()).isTrue();
        // 主体与普通债进出池，ABS 既不出旧池也不进新池
        assertThat(f.states.get("C1")).containsExactly(23L);
        assertThat(f.states.get("B1")).containsExactly(16L);
        assertThat(f.states.get("ABS1")).containsExactly(16L);
        assertThat(f.logs).extracting(IpAdjustLogBo::getSecurityCode).containsExactly("C1", "C1");
        assertThat(f.logs).allMatch(row -> !"ABS1".equals(row.getSecurityCode()));
    }

    /** 主数据不匹配、非法类型、缺失编码和重复主体在任何写入之前失败。 */
    @Test
    public void shouldRejectInvalidSourceBeforeAnyWrites() {
        for (String invalid : Arrays.asList("mapping", "type", "code", "id", "duplicate")) {
            Fixture f = new Fixture();
            CompanyObserverDto row = observer("C1", "2");
            if ("mapping".equals(invalid)) {
                row.setMasterCompanyCode("OTHER");
            } else if ("type".equals(invalid)) {
                row.setObserverType("9");
            } else if ("code".equals(invalid)) {
                row.setCompanyCode(null);
            } else if ("id".equals(invalid)) {
                row.setCompanyId(null);
            } else {
                f.observers.add(observer("C1", "3"));
            }
            f.observers.add(row);
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).as(invalid).isFalse();
            assertThat(result.getAffectedCount()).isZero();
            assertThat(f.logs).isEmpty();
            assertThat(result.getDetailLog()).contains("任务结束（失败，整轮回滚）");
        }
    }

    /** 任意源池及联动目标按配置执行，未配置的17不被硬编码处理。 */
    @Test
    public void shouldReadConfiguredTargetsInsteadOfHardcodingBlacklist() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "2"));
        f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
        when(f.poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"), pool(99L, "other")));
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                relation(16L, 99L, "in_linked"), relation(16L, 99L, "out_linked")));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(2);
        assertThat(f.states.get("C1")).containsExactly(16L, 99L);
        assertThat(f.states).doesNotContainKey("B1");
        f.observers.get(0).setObserverType("1");
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(2);
        assertThat(f.states.get("C1")).isEmpty();
        assertThat(f.states).doesNotContainKey("B1");
        assertThat(f.logs).noneMatch(row -> Long.valueOf(17L).equals(row.getTargetPoolId()));
    }

    /** 联动目标池同样检查实际主体及债券的调入/调出限制。 */
    @Test
    public void shouldRejectLinkedTargetRestrictionsInBothDirections() {
        for (boolean inbound : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.observers.add(observer("C1", inbound ? "3" : "1"));
            f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
            f.states.put("C1", new LinkedHashSet<>(inbound ? Collections.emptyList() : Arrays.asList(23L, 17L)));
            f.states.put("B1", new LinkedHashSet<>(inbound ? Collections.emptyList() : Arrays.asList(23L, 17L)));
            f.states.get("C1").add(15L);
            when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                    relation(23L, 17L, inbound ? "in_linked" : "out_linked"),
                    relation(17L, 15L, inbound ? "in_restrict" : "out_restrict")));
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("C1", "池17", "限制池");
        }
    }

    /** 同批联动调入池可以满足来源池要求，先出后入时也认可本轮原在池依据。 */
    @Test
    public void shouldAcceptPlannedAndOriginalSourcePools() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                relation(23L, 17L, "source"), relation(23L, 17L, "in_linked")));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(2);

        Fixture switching = new Fixture();
        switching.observers.add(observer("C1", "3"));
        switching.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
        switching.states.put("C1", new LinkedHashSet<>(Collections.singletonList(16L)));
        switching.states.put("B1", new LinkedHashSet<>(Collections.singletonList(16L)));
        when(switching.statusMapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation(23L, 16L, "source")));
        assertThat(switching.service.execute().getAffectedCount()).isEqualTo(2);
        assertThat(switching.states.get("B1")).containsExactly(16L);
    }

    /** 两类弹性禁投按现有主体规则只记警告，仍正常完成主体及债券入/出池。 */
    @Test
    public void shouldWarnWithoutBlockingBothSoftRestrictions() {
        for (boolean inbound : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.observers.add(observer("C1", inbound ? "3" : "1"));
            f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
            f.states.put("C1", new LinkedHashSet<>(inbound ? Collections.singletonList(99L) : Arrays.asList(23L, 99L)));
            f.states.put("B1", new LinkedHashSet<>(inbound ? Collections.singletonList(99L) : Arrays.asList(23L, 99L)));
            when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(
                    relation(23L, 99L, inbound ? "in_soft_restrict" : "out_soft_restrict")));
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAffectedCount()).isEqualTo(1);
            assertThat(result.getDetailLog()).contains("WARN", "C1", "弹性禁投池（警告不阻断）")
                    .doesNotContain("B1");
        }
    }

    /** 同批计划调入源池及其互斥联动池时明确阻断，不通过先入后出掩盖冲突。 */
    @Test
    public void shouldRejectMutuallyExclusiveInboundPlan() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                relation(23L, 17L, "in_linked"), relation(23L, 17L, "in_mutex")));
        ScheduledTaskResult result = f.service.execute();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("同批调入池", "冲突");
    }

    /** 互斥调出后后续限制校验应看到实时内存状态，不能沿用预读时的旧状态。 */
    @Test
    public void shouldRefreshStateBetweenMultipleMutexOutboundsWithoutQueries() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
        f.states.put("C1", new LinkedHashSet<>(Arrays.asList(98L, 99L)));
        f.states.put("B1", new LinkedHashSet<>(Arrays.asList(98L, 99L)));
        when(f.poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"),
                pool(98L, "other"), pool(99L, "other")));
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                relation(23L, 97L, "in_mutex"), relation(23L, 98L, "in_mutex"), relation(23L, 99L, "in_mutex"),
                relation(99L, 98L, "out_restrict")));
        ScheduledTaskResult result = f.service.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(3);
        assertThat(f.states.get("C1")).containsExactly(23L);
        assertThat(f.states.get("B1")).containsExactly(98L, 99L);
        verify(f.sourceMapper).queryObserverPoolStatusList(anyList());
        verify(f.statusMapper, never()).querySecurityCurrentPoolIdList(anyString());
    }

    /** 无观察主体及在池主体时不生成空 IN 查询。 */
    @Test
    public void shouldSkipBatchQueriesWhenNoCompaniesNeedProcessing() {
        Fixture f = new Fixture();
        assertThat(f.service.execute().getAffectedCount()).isZero();
        verify(f.sourceMapper, never()).queryObserverPoolStatusList(anyList());
    }

    /** 构造一条池关系。 */
    static PoolRelationBo relation(Long sourcePoolId, Long targetPoolId, String type) {
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(sourcePoolId);
        relation.setRelationPoolId(targetPoolId);
        relation.setRelationType(type);
        return relation;
    }

    /** 构造一条有效的 AIS 名单记录。 */
    static CompanyObserverDto observer(String code, String type) {
        CompanyObserverDto row = new CompanyObserverDto();
        row.setCompanyId(1L);
        row.setCompanyCode(code);
        row.setMasterCompanyCode(code);
        row.setCompanyName("主体" + code);
        row.setObserverType(type);
        return row;
    }

    /** 构造主体或债券候选。 */
    static ScheduledAdjustCandidateDto item(String code, String type) {
        ScheduledAdjustCandidateDto row = new ScheduledAdjustCandidateDto();
        row.setSecurityCode(code);
        row.setSecurityShortName(code);
        row.setSecurityType(type);
        return row;
    }

    /** 构造已启用普通池。 */
    static InvestmentPoolBo pool(Long id, String type) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setPoolName("池" + id);
        pool.setPoolType(type);
        pool.setStatus("enabled");
        pool.setIsDeleted(0);
        return pool;
    }

    /** 使用状态集合模拟真实入出池，重复执行会读取上次实际写入结果。 */
    static class Fixture {
        /** 待测任务 */
        final CompanyObserverPoolSyncService service = AbsIdentityTestSupport.withIdentity(new CompanyObserverPoolSyncService());
        /** 名单查询 */
        final CompanyObserverMapper sourceMapper = mock(CompanyObserverMapper.class);
        /** 债券查询 */
        final AutoAdjustMapper bondMapper = mock(AutoAdjustMapper.class);
        /** 状态写入 */
        final SecurityPoolAdjustMapper statusMapper = mock(SecurityPoolAdjustMapper.class);
        /** 池查询 */
        final InvestmentPoolMapper poolMapper = mock(InvestmentPoolMapper.class);
        /** 定时任务配置查询 */
        final ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        /** 页面保存的扩展参数 */
        final SysScheduledTaskBo task = new SysScheduledTaskBo();
        /** 本轮名单 */
        final List<CompanyObserverDto> observers = new ArrayList<>();
        /** 当前在池或有孤立债券的主体 */
        final List<ScheduledAdjustCandidateDto> currentCompanies = new ArrayList<>();
        /** 主体旗下债券 */
        final Map<String, List<ScheduledAdjustCandidateDto>> bonds = new LinkedHashMap<>();
        /** 实际池状态 */
        final Map<String, Set<Long>> states = new LinkedHashMap<>();
        /** 实际业务日志 */
        final List<IpAdjustLogBo> logs = new ArrayList<>();

        /** 注入依赖，并让候选查询和状态写入共享同一份状态。 */
        Fixture() {
            AutoAdjustTestSupport.bindExcelExclusion(service);
            ReflectionTestUtils.setField(service, "companyObserverMapper", sourceMapper);
            ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", statusMapper);
            ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
            ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
            task.setParamJson("{\"observePoolId\":16,\"keyObservePoolId\":23}");
            when(taskMapper.queryTaskByCode(CompanyObserverPoolSyncService.TASK_CODE)).thenReturn(task);
            when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"), pool(17L, "blacklist")));
            when(sourceMapper.queryCompanyObserverList()).thenReturn(observers);
            when(sourceMapper.queryCurrentCompanyList(anyList())).thenReturn(currentCompanies);
            when(sourceMapper.queryObserverPoolStatusList(anyList())).thenAnswer(call -> {
                List<IpPoolStatusBo> result = new ArrayList<>();
                List<String> codes = call.getArgument(0);
                for (String code : codes) {
                    for (Long poolId : states.getOrDefault(code, Collections.emptySet())) {
                        IpPoolStatusBo row = new IpPoolStatusBo();
                        row.setSecurityCode(code);
                        row.setTargetPoolId(poolId);
                        result.add(row);
                    }
                }
                return result;
            });
            when(statusMapper.addAdjustLog(any(IpAdjustLogBo.class))).thenAnswer(call -> {
                IpAdjustLogBo row = call.getArgument(0);
                row.setId((long) logs.size() + 1);
                logs.add(row);
                return 1;
            });
            when(statusMapper.addPoolStatus(any(IpAdjustLogBo.class))).thenAnswer(call -> {
                IpAdjustLogBo row = call.getArgument(0);
                return states.computeIfAbsent(row.getSecurityCode(), key -> new LinkedHashSet<>()).add(row.getTargetPoolId()) ? 1 : 0;
            });
            when(statusMapper.deleteUnprotectedPoolStatusSoft(anyString(), anyLong())).thenAnswer(call ->
                    states.getOrDefault(call.getArgument(0), Collections.emptySet()).remove(call.getArgument(1)) ? 1 : 0);
        }
    }
}
