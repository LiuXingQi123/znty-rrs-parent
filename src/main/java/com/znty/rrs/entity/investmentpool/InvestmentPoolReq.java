package com.znty.rrs.entity.investmentpool;


import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.flow.FlowOptionDto;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 投资池新增/编辑请求对象，携带投资池配置、关联流程、自动规则及权限信息
 */
@Data
public class InvestmentPoolReq {

    /** 投资池 ID */
    private Long id;

    /** 父级投资池 ID */
    private Long parentId;

    /** 投资池名称 */
    private String poolName;

    /** 投资池类型 */
    private String poolType;

    /** 投资市场编码 */
    private List<String> marketCodes;

    /** 投资品种编码 */
    private List<String> varietyCodes;

    /** 恒生池名称 */
    private String hsPoolName;

    /** 标准调入流程 */
    private FlowOptionDto inFlow;

    /** 标准调出流程 */
    private FlowOptionDto outFlow;

    /** 简易调入流程 */
    private FlowOptionDto simpleInFlow;

    /** 简易调出流程 */
    private FlowOptionDto simpleOutFlow;

    /** 批量调入流程 */
    private FlowOptionDto batchInFlow;

    /** 批量调出流程 */
    private FlowOptionDto batchOutFlow;

    /** 调入研报限制 */
    private String inReportRestriction;

    /** 调出研报限制 */
    private String outReportRestriction;

    /** 最大上限数量 */
    private Long maxCapacity;

    /** 投资池外部排序 */
    private Integer outerSort;

    /** 投资池内部排序 */
    private Integer innerSort;

    /** 投资池描述 */
    private String description;

    /** 关系类型到投资池 ID 列表映射 */
    private Map<String, List<Long>> relationPoolIds;

    /** 自动调入规则 ID 列表 */
    private List<Long> autoInRuleIds;

    /** 自动调入规则备注列表 */
    private List<String> autoInRuleDescs;

    /** 自动调出规则 ID 列表 */
    private List<Long> autoOutRuleIds;

    /** 自动调出规则备注列表 */
    private List<String> autoOutRuleDescs;

    /** 是否继承父级配置 */
    private Boolean inheritParentConfig;

    /** 模板投资池 ID */
    private Long templatePoolId;

    /** 权限配置列表 */
    private List<PoolPermissionBo> permissions;

    /** 角色 ID（人员查询用） */
    private Long roleId;

    /** 搜索关键词（人员查询用） */
    private String keyword;

    /** 经办人 ID */
    private String operatorId;
}
