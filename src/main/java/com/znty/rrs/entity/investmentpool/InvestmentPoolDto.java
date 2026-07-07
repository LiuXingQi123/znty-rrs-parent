package com.znty.rrs.entity.investmentpool;


import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.flow.FlowOptionDto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 投资池 DTO，返回投资池树形结构及关联的流程配置、规则、权限等完整信息
 */
@Data
public class InvestmentPoolDto {

    /** 主键 ID */
    private Long id;

    /** 父级投资池 ID */
    private Long parentId;

    /** 投资池编码 */
    private String poolCode;

    /** 投资池名称 */
    private String poolName;

    /** 投资池全路径名称 */
    private String poolFullName;

    /** 投资池类型 */
    private String poolType;

    /** 投资池层级 */
    private Integer poolLevel;

    /** 投资市场编码 */
    private List<String> marketCodes = new ArrayList<>();

    /** 投资品种编码 */
    private List<String> varietyCodes = new ArrayList<>();

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

    /** 状态 */
    private String status;

    /** 关系类型到投资池 ID 列表映射 */
    private Map<String, List<Long>> relationPoolIds = new HashMap<>();

    /** 自动调入规则 ID 列表 */
    private List<Long> autoInRuleIds = new ArrayList<>();

    /** 自动调入规则备注列表 */
    private List<String> autoInRuleDescs = new ArrayList<>();

    /** 自动调出规则 ID 列表 */
    private List<Long> autoOutRuleIds = new ArrayList<>();

    /** 自动调出规则备注列表 */
    private List<String> autoOutRuleDescs = new ArrayList<>();

    /** 权限配置列表 */
    private List<PoolPermissionBo> permissions = new ArrayList<>();

    /** 子投资池列表 */
    private List<InvestmentPoolDto> children = new ArrayList<>();

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}
