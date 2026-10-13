package com.znty.rrs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.common.enums.EventType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.investmentpool.InvestmentPoolReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 投资池市场、锁池与冻结期配置测试。 */
public class InvestmentPoolServiceTest {

    /** 验证锁池和冻结期能够保存，并在锁池值缺失时按未锁定处理。 */
    @Test
    public void editPoolConfigShouldSaveLockAndFrozenPeriod() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        InvestmentPoolService service = buildService(mapper);
        InvestmentPoolBo oldPool = pool(1L);
        when(mapper.queryPoolById(1L)).thenReturn(oldPool);
        when(mapper.queryPoolByCode("credit_bond_level_1")).thenReturn(null);
        mockDetailLists(mapper, 1L);
        InvestmentPoolReq req = new InvestmentPoolReq();
        req.setId(1L);
        req.setPoolName("信用债一级库");
        req.setPoolCode("credit_bond_level_1");
        req.setPoolType("credit_bond");
        req.setLockFlag(1);
        req.setFrozenPeriodIn(30);

        service.editPoolConfig(req);

        ArgumentCaptor<InvestmentPoolBo> captor = ArgumentCaptor.forClass(InvestmentPoolBo.class);
        verify(mapper).editPoolConfig(captor.capture());
        assertThat(captor.getValue().getLockFlag()).isEqualTo(1);
        assertThat(captor.getValue().getFrozenPeriodIn()).isEqualTo(30);

        req.setLockFlag(null);
        req.setFrozenPeriodIn(0);
        service.editPoolConfig(req);
        verify(mapper, times(2)).editPoolConfig(captor.capture());
        assertThat(captor.getAllValues().get(2).getLockFlag()).isEqualTo(0);
        assertThat(captor.getAllValues().get(2).getFrozenPeriodIn()).isEqualTo(0);
    }

    /** 验证非法锁池值和负冻结期会被阻断。 */
    @Test
    public void editPoolConfigShouldRejectInvalidLockAndFrozenPeriod() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        InvestmentPoolService service = buildService(mapper);
        when(mapper.queryPoolById(1L)).thenReturn(pool(1L));
        InvestmentPoolReq req = new InvestmentPoolReq();
        req.setId(1L);
        req.setPoolCode("credit_bond_level_1");
        req.setPoolType("credit_bond");
        req.setLockFlag(2);

        assertThatThrownBy(() -> service.editPoolConfig(req))
                .isInstanceOf(BizException.class)
                .hasMessage("锁池标志只能为 0 或 1");

        req.setLockFlag(0);
        req.setFrozenPeriodIn(-1);
        assertThatThrownBy(() -> service.editPoolConfig(req))
                .isInstanceOf(BizException.class)
                .hasMessage("调入冻结期不能小于 0 天");
    }

    /** 验证继承父池配置时同步继承锁池和冻结期。 */
    @Test
    public void addChildPoolShouldInheritLockAndFrozenPeriod() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        InvestmentPoolService service = buildService(mapper);
        InvestmentPoolBo parent = pool(1L);
        parent.setPoolCode("credit_bond_root");
        parent.setPoolType("credit_bond");
        parent.setPoolLevel(1);
        parent.setLockFlag(1);
        parent.setFrozenPeriodIn(15);
        AtomicReference<InvestmentPoolBo> saved = new AtomicReference<>();
        when(mapper.queryPoolById(1L)).thenReturn(parent);
        when(mapper.queryPoolById(2L)).thenAnswer(invocation -> saved.get());
        when(mapper.queryMaxInnerSortValue(1L)).thenReturn(1);
        doAnswer(invocation -> {
            InvestmentPoolBo child = (InvestmentPoolBo) invocation.getArguments()[0];
            child.setId(2L);
            saved.set(child);
            return 1;
        }).when(mapper).addPool(any(InvestmentPoolBo.class));
        mockDetailLists(mapper, 2L);
        when(mapper.queryPoolByCode("credit_bond_level_1")).thenReturn(null);
        InvestmentPoolReq req = new InvestmentPoolReq();
        req.setParentId(1L);
        req.setPoolName("信用债一级库");
        req.setPoolCode("credit_bond_level_1");
        req.setPoolType("credit_bond");
        req.setInheritParentConfig(true);

        service.addChildPool(req);

        assertThat(saved.get().getLockFlag()).isEqualTo(1);
        assertThat(saved.get().getFrozenPeriodIn()).isEqualTo(15);
    }

    /** 验证全部合法市场均可保存，包含主体、未知及香港交易所。 */
    @Test
    public void editPoolConfigShouldSaveAllSupportedMarkets() throws Exception {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        // 构建服务并模拟已有池及详情依赖
        InvestmentPoolService service = buildService(mapper);
        when(mapper.queryPoolById(1L)).thenReturn(pool(1L));
        // 模拟保存后的详情查询依赖
        mockDetailLists(mapper, 1L);
        // 构建合法的基础配置请求
        InvestmentPoolReq req = marketConfigReq();
        List<String> markets = Arrays.asList("SSE", "SZSE", "CIBM", "BSE", "COMPANY", "OTC",
                "HKEX", "QDII", "JWCW", "UNKNOWN", "OTHER");
        req.setMarketCodes(markets);

        service.editPoolConfig(req);

        ArgumentCaptor<InvestmentPoolBo> captor = ArgumentCaptor.forClass(InvestmentPoolBo.class);
        verify(mapper).editPoolConfig(captor.capture());
        assertThat(captor.getValue().getMarketCodes()).isEqualTo(new ObjectMapper().writeValueAsString(markets));
        verify(mapper).addPoolEvent(1L, "system", EventType.UPDATE.getCode());
    }

    /** 验证未传及空市场列表仍按空配置保存。 */
    @Test
    public void editPoolConfigShouldSaveEmptyMarketConfiguration() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        // 构建被测服务
        InvestmentPoolService service = buildService(mapper);
        when(mapper.queryPoolById(1L)).thenReturn(pool(1L));
        // 模拟保存后的详情查询依赖
        mockDetailLists(mapper, 1L);
        // 构建未传市场列表的请求
        InvestmentPoolReq req = marketConfigReq();

        service.editPoolConfig(req);
        req.setMarketCodes(Collections.emptyList());
        service.editPoolConfig(req);

        ArgumentCaptor<InvestmentPoolBo> captor = ArgumentCaptor.forClass(InvestmentPoolBo.class);
        verify(mapper, times(2)).editPoolConfig(captor.capture());
        assertThat(captor.getAllValues()).extracting(InvestmentPoolBo::getMarketCodes).containsExactly("[]", "[]");
    }

    /** 验证任意位置的非法市场均阻断保存及审计写入，不自动修正大小写或空格。 */
    @Test
    public void editPoolConfigShouldRejectInvalidMarketEntriesWithoutWriting() {
        InvestmentPoolMapper mapper = mock(InvestmentPoolMapper.class);
        // 构建被测服务
        InvestmentPoolService service = buildService(mapper);
        when(mapper.queryPoolById(1L)).thenReturn(pool(1L));
        // 构建基础配置请求
        InvestmentPoolReq req = marketConfigReq();
        for (String invalid : Arrays.asList("ABC", "IB", "sse", "SSE ", " SSE", "", " ", null)) {
            for (int index = 0; index < 2; index++) {
                req.setMarketCodes(index == 0 ? Arrays.asList(invalid, "SSE") : Arrays.asList("SSE", invalid));
                assertThatThrownBy(() -> service.editPoolConfig(req))
                        .isInstanceOf(BizException.class)
                        .hasMessage("投资市场编码不合法：marketCodes[" + index + "]=" + invalid);
            }
        }
        verify(mapper, never()).editPoolConfig(any(InvestmentPoolBo.class));
        verify(mapper, never()).addPoolEvent(anyLong(), anyString(), anyString());
    }

    /** 构建市场校验测试的合法基础配置请求。 */
    private InvestmentPoolReq marketConfigReq() {
        InvestmentPoolReq req = new InvestmentPoolReq();
        req.setId(1L);
        req.setPoolName("信用债一级库");
        req.setPoolCode("credit_bond_level_1");
        req.setPoolType("credit_bond");
        return req;
    }

    /** 构建被测服务。 */
    private InvestmentPoolService buildService(InvestmentPoolMapper mapper) {
        InvestmentPoolService service = new InvestmentPoolService();
        ReflectionTestUtils.setField(service, "investmentPoolMapper", mapper);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        return service;
    }

    /** 构建投资池记录。 */
    private InvestmentPoolBo pool(Long id) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setPoolCode("credit_bond_level_1");
        pool.setPoolType("credit_bond");
        pool.setMarketCodes("[]");
        pool.setVarietyCodes("[\"bond\"]");
        return pool;
    }

    /** 模拟详情查询依赖的空配置列表。 */
    private void mockDetailLists(InvestmentPoolMapper mapper, Long poolId) {
        when(mapper.queryRelationList(poolId)).thenReturn(Collections.emptyList());
        when(mapper.queryAutoRuleList(poolId)).thenReturn(Collections.emptyList());
        when(mapper.queryPermissionList(poolId)).thenReturn(Collections.emptyList());
    }
}
