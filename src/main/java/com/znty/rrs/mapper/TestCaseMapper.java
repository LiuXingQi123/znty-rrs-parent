package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.RuleTestCaseBo;
import com.znty.rrs.entity.bo.RuleTestCaseParamBo;
import com.znty.rrs.entity.bo.RuleTestRunBo;
import com.znty.rrs.entity.bo.RuleTestRunLogBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 测试用例数据访问接口，统一管理以下表的 SQL 操作：
 * - rule_test_case — 测试用例
 * - rule_test_case_param — 测试用例参数值
 * - rule_test_run — 测试执行记录
 * - rule_test_run_log — 测试执行步骤日志
 *
 * SQL 映射文件：mapper/TestCaseMapper.xml
 */
@Mapper
public interface TestCaseMapper {

    // ==================== rule_test_case ====================

    /**
     * 分页查询测试用例列表，按创建时间倒序
     *
     * @param keyword 用例名称关键字，为空时不筛选
     * @param result 最近一次的测试执行结果
     * @return 符合条件的规则测试用例列表
     */
    List<RuleTestCaseBo> queryCasePage(@Param("keyword") String keyword,
                                       @Param("result") String result);

    /**
     * 查询全部测试用例（用于批量执行）
     *
     * @return 符合条件的规则测试用例列表
     */
    List<RuleTestCaseBo> queryAllCaseList();

    /**
     * 按主键 ID 查询单条测试用例
     *
     * @param id 规则测试用例 ID
     * @return 规则测试用例，未找到时为 null
     */
    RuleTestCaseBo queryCaseById(@Param("id") Long id);

    /**
     * 新增测试用例，主键自增回填
     *
     * @param testCase 待写入的规则测试用例
     * @return 受影响的行数
     */
    int addCase(RuleTestCaseBo testCase);

    /**
     * 按主键更新测试用例基本字段
     *
     * @param testCase 包含 ID 及待修改字段的规则测试用例
     * @return 受影响的行数
     */
    int editCase(RuleTestCaseBo testCase);

    /**
     * 按主键仅更新用例名称
     *
     * @param id 规则测试用例 ID
     * @param caseName 测试用例名称
     * @return 受影响的行数
     */
    int editCaseName(@Param("id") Long id, @Param("caseName") String caseName);

    /**
     * 更新最近一次执行结果（状态、输出、执行时间）
     *
     * @param testCase 包含 ID 及待修改字段的规则测试用例
     * @return 受影响的行数
     */
    int editCaseLastResult(RuleTestCaseBo testCase);

    /**
     * 按主键物理删除测试用例
     *
     * @param id 规则测试用例 ID
     * @return 受影响的行数
     */
    int deleteCaseById(@Param("id") Long id);

    // ==================== rule_test_case_param ====================

    /**
     * 按用例 ID 列表批量查询参数值
     *
     * @param caseIds 测试用例 ID 列表
     * @return 符合条件的测试用例参数列表
     */
    List<RuleTestCaseParamBo> queryParamsByCaseIdsList(@Param("caseIds") List<Long> caseIds);

    /**
     * 新增一条用例参数值，主键自增回填
     *
     * @param param 待写入的测试用例参数
     * @return 受影响的行数
     */
    int addCaseParam(RuleTestCaseParamBo param);

    /**
     * 按用例 ID 删除该用例下的所有参数值
     *
     * @param caseId 测试用例 ID
     * @return 受影响的行数
     */
    int deleteParamsByCaseId(@Param("caseId") Long caseId);

    // ==================== rule_test_run ====================

    /**
     * 新增一条执行记录，含规则 ID、用例 ID、状态、输出和执行时间
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param run 待写入的测试执行记录
     * @return 受影响的行数
     */
    int addRun(RuleTestRunBo run);

    // ==================== rule_test_run_log ====================

    /**
     * 新增一条执行步骤日志，关联执行记录 ID
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param log 待写入的测试执行过程日志
     * @return 受影响的行数
     */
    int addRunLog(RuleTestRunLogBo log);

    /**
     * 按用例 ID 查询执行记录列表，最近优先
     *
     * @param caseId 测试用例 ID
     * @return 符合条件的测试执行记录列表
     */
    List<RuleTestRunBo> queryRunsByCaseIdList(@Param("caseId") Long caseId);

    /**
     * 按执行记录 ID 查询步骤日志列表
     *
     * @param runId 测试执行记录 ID
     * @return 符合条件的测试执行过程日志列表
     */
    List<RuleTestRunLogBo> queryRunLogList(@Param("runId") Long runId);
}
