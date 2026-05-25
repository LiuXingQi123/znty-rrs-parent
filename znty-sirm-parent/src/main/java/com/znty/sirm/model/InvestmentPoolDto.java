package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 投资池返回对象
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

    /** 自动调入规则备注 */
    private String autoInRuleDesc;

    /** 自动调出规则备注 */
    private String autoOutRuleDesc;

    /** 权限配置列表 */
    private List<PoolPermissionBo> permissions = new ArrayList<>();

    /** 子投资池列表 */
    private List<InvestmentPoolDto> children = new ArrayList<>();

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updtTime;
}
