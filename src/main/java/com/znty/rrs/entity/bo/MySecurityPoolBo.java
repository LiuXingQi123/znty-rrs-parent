package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 我的证券池实体，记录用户自定义关注的证券列表（个人自选池）
 */
@Data
public class MySecurityPoolBo {

    /** 主键 ID */
    private Long id;
    /** 证券代码 */
    private String securityCode;
    /** 证券类型：股票/债券/基金/公司... */
    private String securityType;
    /** 证券市场：上交所/深交所/银行间/北交所... */
    private String market;
    /** 用户 ID */
    private String userId;
    /** 状态：use=使用 / del=删除 */
    private String status;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
