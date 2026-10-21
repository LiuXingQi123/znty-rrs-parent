package com.znty.rrs.service;

import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
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
import static org.mockito.Mockito.times;
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
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(2);
        verify(f.sourceMapper).queryCurrentCompanyList(Arrays.asList(116L, 123L));
        f.observers.get(0).setObserverType("3");
        ScheduledTaskResult switched = f.service.execute();
        assertThat(switched.isSuccess()).isTrue();
        assertThat(switched.getAffectedCount()).isEqualTo(6);
        assertThat(switched.getDetailLog()).contains("2=116", "3=123", "源池=池123", "配置池=[117]");
        assertThat(f.states.get("C1")).containsExactly(16L, 23L, 123L, 117L);
        assertThat(f.states.get("B1")).containsExactly(16L, 23L, 123L, 117L);
        assertThat(f.logs).allSatisfy(row -> assertThat(row.getTargetPoolId()).isIn(116L, 123L, 117L));
        f.observers.clear();
        f.currentCompanies.add(item("C1", "company"));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(4);
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

    /** 两种观察类型进入各自目标池，旗下 ABS/CRMW 沿用主体批次且只写原因。 */
    @Test
    public void shouldEnterMappedPoolsWithBondsAndCompanyBatches() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "2"));
        f.observers.add(observer("C2", "3"));
        f.bonds.put("C1", Collections.singletonList(item("ABS1", "abs")));
        f.bonds.put("C2", Collections.singletonList(item("CRMW1", "crmw")));
        ScheduledTaskResult result = f.service.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(4);
        assertThat(f.states.get("C1")).containsExactly(16L);
        assertThat(f.states.get("ABS1")).containsExactly(16L);
        assertThat(f.states.get("C2")).containsExactly(23L);
        assertThat(f.states.get("CRMW1")).containsExactly(23L);
        for (IpAdjustLogBo row : f.logs) {
            assertThat(row.getAuditStatus()).isEqualTo("20");
            assertThat(row.getAdjusterId()).isEqualTo("0");
            assertThat(row.getAdjustType()).isEqualTo("自动调整");
            assertThat(row.getAdjustBatchNo()).startsWith("COMP");
            assertThat(row.getAdjustAdvice()).isNull();
            assertThat(row.getAdjustLogId()).isEqualTo(row.getId());
        }
        assertThat(f.logs.get(0).getAdjustBatchNo()).isEqualTo(f.logs.get(1).getAdjustBatchNo());
        assertThat(f.logs.get(2).getAdjustBatchNo()).isEqualTo(f.logs.get(3).getAdjustBatchNo())
                .isNotEqualTo(f.logs.get(0).getAdjustBatchNo());
        assertThat(result.getDetailLog()).contains("扫描条件：", "扫描完成：", "任务结束（成功）")
                .doesNotContain(f.logs.get(0).getAdjustBatchNo());
    }

    /** 类型从观察切换到重点观察时，先退出主体及债券旧池，再进入新池，重复执行不重复记账。 */
    @Test
    public void shouldMoveBothDirectionsBeforeInboundAndRemainIdempotent() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        f.bonds.put("C1", Collections.singletonList(item("B1", "company_bond")));
        f.states.put("C1", new LinkedHashSet<>(Collections.singletonList(16L)));
        f.states.put("B1", new LinkedHashSet<>(Collections.singletonList(16L)));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(4);
        assertThat(f.logs).extracting(IpAdjustLogBo::getAdjustMode).containsExactly("调出", "调出", "调入", "调入");
        assertThat(f.states.get("C1")).containsExactly(23L);
        assertThat(f.states.get("B1")).containsExactly(23L);
        ScheduledTaskResult repeated = f.service.execute();
        assertThat(repeated.isSuccess()).isTrue();
        assertThat(repeated.getAffectedCount()).isZero();
        assertThat(f.logs).hasSize(4);
    }

    /** 类型1、空值和名单移除都退出对应池；主体已出但旗下债仍在时也能清理。 */
    @Test
    public void shouldExitForNoneBlankAndRemovedObserversIncludingOrphanBonds() {
        for (String type : Arrays.asList("1", null, "", "removed")) {
            Fixture f = new Fixture();
            if (!"removed".equals(type)) {
                f.observers.add(observer("C1", type));
            }
            f.currentCompanies.add(item("C1", "company"));
            f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
            f.states.put("B1", new LinkedHashSet<>(Collections.singletonList(23L)));
            if (!"1".equals(type)) {
                f.states.put("C1", new LinkedHashSet<>(Collections.singletonList(23L)));
            }
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).as("type=" + type).isTrue();
            assertThat(f.states.get("B1")).isEmpty();
            assertThat(f.logs).allMatch(row -> "调出".equals(row.getAdjustMode()));
        }
    }

    /** 主体已经在正确池时仍补齐旗下新债，不生成重复主体日志。 */
    @Test
    public void shouldRepairMissingBondsWhenCompanyAlreadyInPool() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "2"));
        f.states.put("C1", new LinkedHashSet<>(Collections.singletonList(16L)));
        f.bonds.put("C1", Collections.singletonList(item("B1", "abs")));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(1);
        assertThat(f.logs).extracting(IpAdjustLogBo::getSecurityCode).containsExactly("B1");
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

    /** 主体或旗下债券命中调入/调出限制均返回失败，交由整轮事务回滚。 */
    @Test
    public void shouldRejectRestrictedCompanyOrBondForBothDirections() {
        for (boolean inbound : new boolean[]{false, true}) {
            for (boolean companyRestricted : new boolean[]{false, true}) {
                Fixture f = new Fixture();
                f.observers.add(observer("C1", inbound ? "2" : "1"));
                f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
                f.states.put("C1", new LinkedHashSet<>(inbound ? Collections.emptyList() : Collections.singletonList(16L)));
                f.states.put("B1", new LinkedHashSet<>(inbound ? Collections.emptyList() : Collections.singletonList(16L)));
                f.states.get(companyRestricted ? "C1" : "B1").add(15L);
                PoolRelationBo relation = new PoolRelationBo();
                relation.setPoolId(16L);
                relation.setRelationType(inbound ? "in_restrict" : "out_restrict");
                relation.setRelationPoolId(15L);
                when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation));
                ScheduledTaskResult result = f.service.execute();
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getMessage()).contains(companyRestricted ? "C1" : "B1", "限制池");
            }
        }
    }

    /** 债券入池后按互斥/反向限制出原池，关系日志沿用主体批次。 */
    @Test
    public void shouldApplyBondRelationOutWithCompanyBatch() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "2"));
        f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
        f.states.put("B1", new LinkedHashSet<>(Collections.singletonList(99L)));
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(16L);
        relation.setRelationType("in_mutex");
        relation.setRelationPoolId(99L);
        InvestmentPoolBo other = pool(99L, "other");
        when(f.poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"), other));
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(3);
        assertThat(f.states.get("B1")).containsExactly(16L);
        assertThat(f.logs.get(2).getAdjustType()).isEqualTo("互斥调整");
        assertThat(f.logs.get(2).getAdjustBatchNo()).isEqualTo(f.logs.get(0).getAdjustBatchNo());
    }

    /** 重点观察源池的双向联动即时同步17及旗下债，同批次、去重且可重复执行。 */
    @Test
    public void shouldSynchronizeLinkedBlacklistBothDirectionsWithSourceBatch() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        f.bonds.put("C1", Arrays.asList(item("ABS1", "abs"), item("CRMW1", "crmw")));
        PoolRelationBo linkedIn = relation(23L, 17L, "in_linked");
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                linkedIn, linkedIn, relation(23L, 23L, "in_linked"), relation(23L, 17L, "out_linked")));
        ScheduledTaskResult entered = f.service.execute();
        assertThat(entered.isSuccess()).isTrue();
        assertThat(entered.getAffectedCount()).isEqualTo(6);
        for (String code : Arrays.asList("C1", "ABS1", "CRMW1")) {
            assertThat(f.states.get(code)).containsExactly(23L, 17L);
        }
        assertThat(f.logs).extracting(IpAdjustLogBo::getAdjustBatchNo).containsOnly(f.logs.get(0).getAdjustBatchNo());
        assertThat(f.logs.subList(3, 6)).allMatch(row -> "联动调整".equals(row.getAdjustType()));
        assertThat(entered.getDetailLog()).contains("in_linked/out_linked", "联动扫描：");
        assertThat(entered.getMessage()).contains("池17：主体入1/出0，债券入2/出0");
        assertThat(f.service.execute().getAffectedCount()).isZero();
        assertThat(f.logs).hasSize(6);

        f.observers.get(0).setObserverType("2");
        ScheduledTaskResult exited = f.service.execute();
        assertThat(exited.isSuccess()).isTrue();
        assertThat(exited.getAffectedCount()).isEqualTo(9);
        for (String code : Arrays.asList("C1", "ABS1", "CRMW1")) {
            assertThat(f.states.get(code)).containsExactly(16L);
        }
        assertThat(f.logs.subList(6, 12)).allMatch(row -> "调出".equals(row.getAdjustMode()));
        assertThat(f.logs.subList(6, 12)).extracting(IpAdjustLogBo::getAdjustBatchNo)
                .containsOnly(f.logs.get(6).getAdjustBatchNo());
        assertThat(f.logs.subList(9, 12)).allMatch(row -> "联动调整".equals(row.getAdjustType()));
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
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(4);
        assertThat(f.states.get("C1")).containsExactly(16L, 99L);
        assertThat(f.states.get("B1")).containsExactly(16L, 99L);
        f.observers.get(0).setObserverType("1");
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(4);
        assertThat(f.states.get("C1")).isEmpty();
        assertThat(f.states.get("B1")).isEmpty();
        assertThat(f.logs).noneMatch(row -> Long.valueOf(17L).equals(row.getTargetPoolId()));
    }

    /** 源池状态不变也补齐联动池主体及旗下新债；源池无实际退出时不误出无关17状态。 */
    @Test
    public void shouldRepairLinkedDifferencesWithoutRemovingUnrelatedStatus() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
        f.states.put("C1", new LinkedHashSet<>(Collections.singletonList(23L)));
        f.states.put("B1", new LinkedHashSet<>(Collections.singletonList(23L)));
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                relation(23L, 17L, "in_linked"), relation(23L, 17L, "out_linked")));
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(2);
        assertThat(f.logs).allMatch(row -> Long.valueOf(17L).equals(row.getTargetPoolId()));
        f.states.get("B1").remove(17L);
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(1);
        f.observers.get(0).setObserverType("1");
        f.states.get("C1").remove(23L);
        f.states.get("B1").remove(23L);
        assertThat(f.service.execute().getAffectedCount()).isZero();
        assertThat(f.states.get("C1")).containsExactly(17L);
        assertThat(f.states.get("B1")).containsExactly(17L);
    }

    /** 联动目标池同样检查实际主体及债券的调入/调出限制。 */
    @Test
    public void shouldRejectLinkedTargetRestrictionsInBothDirections() {
        for (boolean inbound : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            f.observers.add(observer("C1", inbound ? "3" : "1"));
            f.bonds.put("C1", Collections.singletonList(item("B1", "abs")));
            f.states.put("C1", new LinkedHashSet<>(inbound ? Collections.emptyList() : Arrays.asList(23L, 17L)));
            f.states.put("B1", new LinkedHashSet<>(inbound ? Collections.emptyList() : Arrays.asList(23L, 17L)));
            f.states.get("B1").add(15L);
            when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                    relation(23L, 17L, inbound ? "in_linked" : "out_linked"),
                    relation(17L, 15L, inbound ? "in_restrict" : "out_restrict")));
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("B1", "池17", "限制池");
        }
    }

    /** 来源池取任一命中，主体与债券分别校验，不满足时阻断。 */
    @Test
    public void shouldRequireAnySourcePoolForCompanyAndBond() {
        for (String missing : Arrays.asList("none", "company", "bond")) {
            Fixture f = new Fixture();
            f.observers.add(observer("C1", "2"));
            f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
            if (!"company".equals(missing)) {
                f.states.put("C1", new LinkedHashSet<>(Collections.singletonList(98L)));
            }
            if (!"bond".equals(missing)) {
                f.states.put("B1", new LinkedHashSet<>(Collections.singletonList(98L)));
            }
            when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                    relation(16L, 99L, "source"), relation(16L, 98L, "source")));
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).as(missing).isEqualTo("none".equals(missing));
            if (!result.isSuccess()) {
                assertThat(result.getMessage()).contains("来源池", "company".equals(missing) ? "C1" : "B1");
            }
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
        assertThat(f.service.execute().getAffectedCount()).isEqualTo(4);

        Fixture switching = new Fixture();
        switching.observers.add(observer("C1", "3"));
        switching.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
        switching.states.put("C1", new LinkedHashSet<>(Collections.singletonList(16L)));
        switching.states.put("B1", new LinkedHashSet<>(Collections.singletonList(16L)));
        when(switching.statusMapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation(23L, 16L, "source")));
        assertThat(switching.service.execute().getAffectedCount()).isEqualTo(4);
        assertThat(switching.states.get("B1")).containsExactly(23L);
    }

    /** 调出互斥按主体链路对当前命中对象阻断，主体和债券均生效。 */
    @Test
    public void shouldRejectOutboundMutexForCompanyAndBond() {
        for (String blockedCode : Arrays.asList("C1", "B1")) {
            Fixture f = new Fixture();
            f.observers.add(observer("C1", "1"));
            f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
            f.states.put("C1", new LinkedHashSet<>(Collections.singletonList(23L)));
            f.states.put("B1", new LinkedHashSet<>(Collections.singletonList(23L)));
            f.states.get(blockedCode).add(99L);
            when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation(23L, 99L, "out_mutex")));
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains(blockedCode, "调出互斥池");
        }
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
            assertThat(result.getAffectedCount()).isEqualTo(2);
            assertThat(result.getDetailLog()).contains("WARN", "C1", "弹性禁投池（警告不阻断）命中1只")
                    .doesNotContain("B1");
        }
    }

    /** 主体和旗下债券都执行互斥退出，配套调出不能绕过目标池的硬限制或互斥规则。 */
    @Test
    public void shouldApplyCompanyMutexAndValidateGeneratedOutbound() {
        for (String outRule : Arrays.asList("none", "out_restrict", "out_mutex")) {
            Fixture f = new Fixture();
            f.observers.add(observer("C1", "3"));
            f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
            f.states.put("C1", new LinkedHashSet<>(Collections.singletonList(99L)));
            f.states.put("B1", new LinkedHashSet<>(Arrays.asList(99L, 15L)));
            when(f.poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"), pool(99L, "other")));
            List<PoolRelationBo> relations = new ArrayList<>();
            relations.add(relation(23L, 99L, "in_mutex"));
            if (!"none".equals(outRule)) {
                relations.add(relation(99L, 15L, outRule));
            }
            when(f.statusMapper.queryAllPoolRelationList()).thenReturn(relations);
            ScheduledTaskResult result = f.service.execute();
            assertThat(result.isSuccess()).isEqualTo("none".equals(outRule));
            if (result.isSuccess()) {
                assertThat(result.getAffectedCount()).isEqualTo(4);
                assertThat(f.states.get("C1")).containsExactly(23L);
                assertThat(f.states.get("B1")).containsExactly(15L, 23L);
                assertThat(f.logs).filteredOn(row -> "互斥调整".equals(row.getAdjustType())).hasSize(2);
                assertThat(f.service.execute().getAffectedCount()).isZero();
            } else {
                assertThat(result.getMessage()).contains("B1", "池99");
            }
        }
    }

    /** 主体和债券已在目标池时也修复互斥双池，不要求先产生新的调入日志。 */
    @Test
    public void shouldRepairExistingCompanyAndBondMutexOverlap() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        f.bonds.put("C1", Collections.singletonList(item("B1", "mtn")));
        f.states.put("C1", new LinkedHashSet<>(Arrays.asList(23L, 99L)));
        f.states.put("B1", new LinkedHashSet<>(Arrays.asList(23L, 99L)));
        when(f.poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"), pool(99L, "other")));
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation(23L, 99L, "in_mutex")));
        ScheduledTaskResult result = f.service.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        assertThat(f.logs).allMatch(row -> "互斥调整".equals(row.getAdjustType()) && "调出".equals(row.getAdjustMode()));
        assertThat(f.states.get("C1")).containsExactly(23L);
        assertThat(f.states.get("B1")).containsExactly(23L);
        assertThat(f.service.execute().getAffectedCount()).isZero();
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

    /** 大量旗下债券只输出按主体、目标池的数量，联动、互斥和柔性警告均不逐券输出。 */
    @Test
    public void shouldAggregateBondProcessLogsByCompanyAndPool() {
        Fixture f = new Fixture();
        f.observers.add(observer("C1", "3"));
        List<ScheduledAdjustCandidateDto> bonds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String code = "BOND_DETAIL_" + i;
            bonds.add(item(code, "mtn"));
            f.states.put(code, new LinkedHashSet<>(Arrays.asList(99L, 15L)));
        }
        f.bonds.put("C1", bonds);
        when(f.poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"),
                pool(17L, "blacklist"), pool(99L, "other")));
        when(f.statusMapper.queryAllPoolRelationList()).thenReturn(Arrays.asList(
                relation(23L, 17L, "in_linked"), relation(23L, 17L, "out_linked"), relation(23L, 99L, "in_mutex"),
                relation(23L, 15L, "in_soft_restrict"), relation(23L, 15L, "out_soft_restrict"),
                relation(17L, 15L, "in_soft_restrict"), relation(17L, 15L, "out_soft_restrict"),
                relation(99L, 15L, "out_soft_restrict")));
        ScheduledTaskResult inbound = f.service.execute();
        assertThat(inbound.isSuccess()).isTrue();
        assertThat(inbound.getAffectedCount()).isEqualTo(302);
        assertThat(inbound.getDetailLog()).contains("处理成功：主体[C1] 调入 池23", "处理成功：主体[C1] 调入 池17",
                "主体[C1]旗下债券：投资池=池23，调入100只，调出0只",
                "主体[C1]旗下债券：投资池=池99，调入0只，调出100只",
                "主体[C1]旗下债券：投资池=池17，调入100只，调出0只",
                "调入弹性禁投池（警告不阻断）命中100只", "调出弹性禁投池（警告不阻断）命中100只")
                .doesNotContain("BOND_DETAIL_");
        assertThat(inbound.getDetailLog().split("\\R")).hasSizeLessThan(25);
        assertThat(f.logs).hasSize(302);

        f.observers.get(0).setObserverType("1");
        ScheduledTaskResult outbound = f.service.execute();
        assertThat(outbound.isSuccess()).isTrue();
        assertThat(outbound.getAffectedCount()).isEqualTo(202);
        assertThat(outbound.getDetailLog()).contains("处理成功：主体[C1] 调出 池23", "处理成功：主体[C1] 调出 池17",
                "主体[C1]旗下债券：投资池=池23，调入0只，调出100只",
                "主体[C1]旗下债券：投资池=池17，调入0只，调出100只")
                .doesNotContain("BOND_DETAIL_");
        assertThat(outbound.getDetailLog().split("\\R")).hasSizeLessThan(25);
        assertThat(f.logs).hasSize(504);
        ScheduledTaskResult repeated = f.service.execute();
        assertThat(repeated.getAffectedCount()).isZero();
        assertThat(repeated.getDetailLog()).doesNotContain("旗下债券：投资池=");
    }

    /** 超过一批的主体及债券仅按编码分批预读，业务循环不再触发逐项查询。 */
    @Test
    public void shouldBoundReadQueriesByBatchCountInsteadOfItemCount() {
        Fixture f = new Fixture();
        for (int index = 0; index < 1001; index++) {
            String companyCode = "BULK_C" + index;
            f.observers.add(observer(companyCode, "2"));
            f.bonds.put(companyCode, Arrays.asList(item("BULK_A" + index, "mtn"), item("BULK_B" + index, "abs")));
        }
        ScheduledTaskResult result = f.service.execute();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(3003);
        verify(f.sourceMapper, times(2)).queryObserverBondList(anyList(), any(CompanyBondTypeScopeBo.class));
        verify(f.sourceMapper, times(4)).queryObserverPoolStatusList(anyList());
        verify(f.statusMapper, never()).querySecurityCurrentPoolIdList(anyString());
        verifyNoInteractions(f.bondMapper);
        assertThat(result.getDetailLog()).contains("债券查询2次，池状态查询4次");
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
        assertThat(result.getAffectedCount()).isEqualTo(6);
        assertThat(f.states.get("C1")).containsExactly(23L);
        assertThat(f.states.get("B1")).containsExactly(23L);
        verify(f.sourceMapper).queryObserverBondList(anyList(), any(CompanyBondTypeScopeBo.class));
        verify(f.sourceMapper).queryObserverPoolStatusList(anyList());
        verify(f.statusMapper, never()).querySecurityCurrentPoolIdList(anyString());
    }

    /** 无观察主体及在池主体时不生成空 IN 查询。 */
    @Test
    public void shouldSkipBatchQueriesWhenNoCompaniesNeedProcessing() {
        Fixture f = new Fixture();
        assertThat(f.service.execute().getAffectedCount()).isZero();
        verify(f.sourceMapper, never()).queryObserverBondList(anyList(), any(CompanyBondTypeScopeBo.class));
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
        final CompanyObserverPoolSyncService service = new CompanyObserverPoolSyncService();
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
            ReflectionTestUtils.setField(service, "companyObserverMapper", sourceMapper);
            ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", statusMapper);
            ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
            ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
            task.setParamJson("{\"observePoolId\":16,\"keyObservePoolId\":23}");
            when(taskMapper.queryTaskByCode(CompanyObserverPoolSyncService.TASK_CODE)).thenReturn(task);
            when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(pool(16L, "observe"), pool(23L, "restricted"), pool(17L, "blacklist")));
            when(sourceMapper.queryCompanyObserverList()).thenReturn(observers);
            when(sourceMapper.queryCurrentCompanyList(anyList())).thenReturn(currentCompanies);
            when(sourceMapper.queryObserverBondList(anyList(), any(CompanyBondTypeScopeBo.class))).thenAnswer(call -> {
                List<ScheduledAdjustCandidateDto> result = new ArrayList<>();
                List<String> companyCodes = call.getArgument(0);
                for (String companyCode : companyCodes) {
                    for (ScheduledAdjustCandidateDto bond : bonds.getOrDefault(companyCode, Collections.emptyList())) {
                        bond.setIssuerCode(companyCode);
                        result.add(bond);
                    }
                }
                return result;
            });
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
            when(statusMapper.deletePoolStatusSoft(anyString(), anyLong())).thenAnswer(call ->
                    states.getOrDefault(call.getArgument(0), Collections.emptySet()).remove(call.getArgument(1)) ? 1 : 0);
        }
    }
}
