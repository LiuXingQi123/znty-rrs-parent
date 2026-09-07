package com.znty.rrs.entity.bo;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 主体旗下债券类型范围。
 *
 * <p>当前统一口径是不排除任何 {@code bond} 大类子类型，因此普通债、ABS、CRMW 均参与主体联动。
 * 若后续某条链路需要缩小范围，应通过本对象显式传入排除条件，避免在 Mapper XML 中重新写死类型。
 * CRMW 作为主体旗下证券进入普通风险池时写 {@code ip_pool_status}；只有“CRMW 凭证 + 标的证券”组合调库
 * 才写 {@code ip_pool_status_crmw}。</p>
 */
@Data
public class CompanyBondTypeScopeBo {

    /** 是否排除 ABS（abs_flag=1 或 security_type=abs） */
    private boolean excludeAbs;

    /** 需排除的证券类型编码，空集合表示不按 security_type 排除 */
    private List<String> excludedSecurityTypes;

    /**
     * 构建包含全部 bond 大类子类型的范围。
     *
     * @return 不排除 ABS、CRMW 或其他 bond 子类型的范围
     */
    public static CompanyBondTypeScopeBo includeAllBondTypes() {
        CompanyBondTypeScopeBo scope = new CompanyBondTypeScopeBo();
        scope.setExcludeAbs(false);
        scope.setExcludedSecurityTypes(Collections.<String>emptyList());
        return scope;
    }

    /**
     * 构建带显式排除条件的范围，供后续差异化业务使用。
     *
     * <p>调用示例见 {@code CompanyBondSyncPolicy#currentTypeScope()}：
     * {@code excluding(true, Arrays.asList("crmw"))} 排除 ABS 和 CRMW；
     * {@code excluding(false, Arrays.asList("crmw"))} 只排除 CRMW；
     * {@code excluding(true, Collections.emptyList())} 只排除 ABS。</p>
     *
     * @param excludeAbs 是否排除 ABS
     * @param excludedSecurityTypes 需排除的证券类型编码
     * @return 防御性复制后的债券类型范围
     */
    public static CompanyBondTypeScopeBo excluding(boolean excludeAbs, List<String> excludedSecurityTypes) {
        CompanyBondTypeScopeBo scope = new CompanyBondTypeScopeBo();
        scope.setExcludeAbs(excludeAbs);
        scope.setExcludedSecurityTypes(excludedSecurityTypes == null
                ? Collections.<String>emptyList() : new ArrayList<>(excludedSecurityTypes));
        return scope;
    }
}
