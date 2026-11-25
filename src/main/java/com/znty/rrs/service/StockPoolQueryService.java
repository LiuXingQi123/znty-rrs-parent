package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.util.QueryListExportHelper;
import com.znty.rrs.common.util.StockQueryFilterHelper;
import com.znty.rrs.entity.bo.MySecurityPoolBo;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.stockpoolquery.*;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.MySecurityPoolMapper;
import com.znty.rrs.mapper.StockPoolQueryMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询和导出已生效的股票池记录
 *
 * 按池、股票和筛选条件分页查询，Excel 导出沿用列表筛选条件并展示池全路径
 * 管理当前用户对股票的收藏，查询时返回其已收藏的股票代码
 */
@Service
public class StockPoolQueryService {
    /** 股票池查询组件 */
    @Resource private StockPoolQueryMapper stockPoolQueryMapper;

    /** 共用个人自选存储组件 */
    @Resource private MySecurityPoolMapper mySecurityPoolMapper;

    /** 投资池路径查询服务 */
    @Resource private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询审批通过的当前股票池状态
     *
     * 执行顺序：
     * 1. 校验用户和日期筛选范围
     * 2. 按筛选条件分页查询
     * 3. 补齐池全路径，并返回本页记录和总条数
     *
     * @param req 当前用户、股票池筛选条件和分页参数
     * @return 查询记录及分页信息
     */
    public PageResult<StockPoolQueryDto> queryStockPoolPage(StockPoolQueryReq req) {
        // 1. 校验用户和日期筛选范围
        validateRequest(req);
        // 2. 按筛选条件分页查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<StockPoolQueryDto> list = stockPoolQueryMapper.queryStockPoolPage(req);
        PageInfo<StockPoolQueryDto> page = new PageInfo<>(list);
        // 3. 补齐池全路径，并返回本页记录和总条数
        fillPoolFullName(list);
        return new PageResult<>(list, page.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 导出全部匹配记录，复用与列表相同的筛选 SQL
     *
     * 执行顺序：
     * 1. 校验导出与分页请求共用的过滤条件
     * 2. 先回填路径，再转换为不包含操作列的导出行
     *
     * @param req 当前用户及股票池导出使用的筛选条件
     * @return 股票投资池Excel
     */
    public CommonFileDto exportStockPoolExcel(StockPoolQueryReq req) {
        // 1. 校验导出与分页请求共用的过滤条件
        validateRequest(req);
        List<StockPoolQueryDto> list = stockPoolQueryMapper.queryStockPoolPage(req);
        // 2. 先回填路径，再转换为不包含操作列的导出行
        fillPoolFullName(list);
        List<StockPoolQueryExportDto> rows = new ArrayList<>();
        for (StockPoolQueryDto source : list) {
            StockPoolQueryExportDto row = new StockPoolQueryExportDto();
            row.setStockName(source.getStockName());
            row.setStockCode(source.getStockCode());
            row.setIndustryName(source.getIndustryName());
            row.setLatestRatingLabel(StockQueryFilterHelper.ratingLabel(source.getLatestRating()));
            row.setPreviousRatingLabel(StockQueryFilterHelper.ratingLabel(source.getPreviousRating()));
            row.setTargetPoolName(source.getTargetPoolName());
            row.setAdjusterName(source.getAdjusterName());
            row.setEntryTime(QueryListExportHelper.formatDateTime(source.getEntryTime()));
            rows.add(row);
        }
        return QueryListExportHelper.fillTemplate("/xlsx/stock_pool_query_export_template.xlsx",
                "股票池查询", "股票池查询导出模板不存在", "生成股票池查询 Excel 失败：", rows);
    }

    /**
     * 幂等添加股票收藏；锁定基础主行以防并发重复插入
     *
     * 执行顺序：
     * 1. 校验用户和基础数据，仅采用后端核实的股票品种及市场
     * 2. 添加股票自选失败时终止处理
     *
     * @param req 当前用户 ID 和要收藏的股票代码
     * @return 用户个人收藏记录
     */
    @Transactional(rollbackFor = Exception.class)
    public MySecurityPoolBo addStockToMyPool(MyStockPoolReq req) {
        // 校验用户和基础数据，仅采用后端核实的股票品种及市场
        MySecurityPoolBo stock = requireFavoriteStock(req);
        MySecurityPoolBo existing = mySecurityPoolMapper.queryByUserAndCode(req.getCurrentUserId(), req.getStockCode());
        if (existing != null) {
            if (!stock.getSecurityType().equals(existing.getSecurityType()) || !stock.getMarket().equals(existing.getMarket())) {
                throw new BizException("该代码已存在其他品种或市场的自选记录，请核实基础数据");
            }
            return existing;
        }
        stock.setUserId(req.getCurrentUserId());
        stock.setStatus("use");
        if (mySecurityPoolMapper.addSecurityToMyPool(stock) != 1 || stock.getId() == null) {
            throw new BizException("添加股票自选失败，请刷新后重试");
        }
        return stock;
    }

    /**
     * 幂等移除本人股票收藏，其他用户和其他品种不受影响
     *
     * 执行顺序：
     * 1. 在与添加相同的主行锁下核实股票归属并删除个人记录
     *
     * @param req 当前用户 ID 和要移除收藏的股票代码
     * @return 用户个人收藏记录
     */
    @Transactional(rollbackFor = Exception.class)
    public MySecurityPoolBo deleteStockFromMyPool(MyStockPoolReq req) {
        // 在与添加相同的主行锁下核实股票归属并删除个人记录
        MySecurityPoolBo stock = requireFavoriteStock(req);
        MySecurityPoolBo existing = mySecurityPoolMapper.queryByUserAndCode(req.getCurrentUserId(), req.getStockCode());
        stockPoolQueryMapper.deleteStockFromMyPool(req.getCurrentUserId(), req.getStockCode(), stock.getSecurityType(), stock.getMarket());
        return existing;
    }

    /**
     * 查询本人有效股票收藏代码
     *
     * 执行顺序：
     * 1. 批量查询用户已收藏的证券代码
     *
     * @param req 要查询股票收藏的当前用户 ID
     * @return 本人有效股票收藏代码列表
     */
    public List<String> queryFavoritedCodeList(MyStockPoolReq req) {
        return stockPoolQueryMapper.queryFavoritedCodeList(StockQueryFilterHelper.requireUserId(req.getCurrentUserId()));
    }

    /**
     * 校验股票收藏参数并锁定真实股票基础记录
     *
     * 执行顺序：
     * 1. 检查股票代码不能为空
     * 2. 股票不存在或股票品种、市场基础数据无效时终止处理
     *
     * @param req 当前用户 ID 和必须存在的股票代码
     * @return 用户个人收藏记录
     */
    private MySecurityPoolBo requireFavoriteStock(MyStockPoolReq req) {
        StockQueryFilterHelper.requireUserId(req.getCurrentUserId());
        if (req.getStockCode() == null || req.getStockCode().trim().isEmpty()) {
            throw new BizException("股票代码不能为空");
        }
        req.setStockCode(req.getStockCode().trim());
        MySecurityPoolBo stock = stockPoolQueryMapper.queryStockForFavorite(req.getStockCode());
        if (stock == null || stock.getSecurityType() == null || stock.getMarket() == null) {
            throw new BizException("股票不存在或股票品种、市场基础数据无效");
        }
        return stock;
    }

    /**
     * 校验用户及日期过滤条件
     *
     * 执行顺序：
     * 1. 先校验当前用户 ID
     * 2. 校验日期范围，把截止日期的次日零点写回请求作为排他上限
     *
     * @param req 股票池查询条件，包含当前用户和入池日期范围
     */
    private void validateRequest(StockPoolQueryReq req) {
        StockQueryFilterHelper.requireUserId(req.getCurrentUserId());
        req.setEntryTimeEndExclusive(StockQueryFilterHelper.endExclusive(req.getEntryTimeStart(), req.getEntryTimeEnd()));
    }

    /**
     * 为页面和导出填充投资池完整路径
     *
     * 执行顺序：
     * 1. 查询全部投资池 ID → 全路径名称映射
     *
     * @param list 本次处理的数据列表
     */
    private void fillPoolFullName(List<StockPoolQueryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        Map<Long, String> names = investmentPoolService.queryPoolFullNameMap();
        for (StockPoolQueryDto row : list) {
            if (names.get(row.getTargetPoolId()) != null) {
                row.setTargetPoolName(names.get(row.getTargetPoolId()));
            }
        }
    }
}
