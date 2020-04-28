package com.xwbing.service.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.metadata.holder.ReadSheetHolder;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xwbing.config.redis.RedisService;
import com.xwbing.constant.ImportStatusEnum;
import com.xwbing.domain.entity.rest.ImportFailLog;
import com.xwbing.domain.entity.rest.ImportTask;
import com.xwbing.domain.entity.vo.ExcelVo;
import com.xwbing.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * analysisEventListener不能被spring管理，要每次读取excel都要new，如果里面用到springBean可以用构造方法传进去
 *
 * @author daofeng
 * @version $Id$
 * @since 2020年04月14日 下午9:51
 */
@Slf4j
public class ExcelReadListener extends AnalysisEventListener<ExcelVo> {
    /**
     * 每隔500条处理数据,然后清理list,方便内存回收
     */
    private static final int BATCH_COUNT = 500;
    private static final int MAX_COUNT = 50000;
    private static final int SAMPLE_LINES = 0;
    private List<CompletableFuture> completableFutures = new ArrayList<>();
    private List<ExcelVo> list = new ArrayList<>();
    private int totalCount;
    private final String importId;
    private final int headRowNum;
    private final EasyExcelDealService easyExcelDealService;
    private final RedisService redisService;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ImportTaskService importTaskService;
    private final ImportFailLogService importFailLogService;

    public ExcelReadListener(RedisService redisService, String importId, int headRowNum,
            EasyExcelDealService easyExcelDealService, ThreadPoolTaskExecutor taskExecutor,
            ImportTaskService importTaskService, ImportFailLogService importFailLogService) {
        this.redisService = redisService;
        this.importId = importId;
        this.headRowNum = headRowNum;
        this.easyExcelDealService = easyExcelDealService;
        this.taskExecutor = taskExecutor;
        this.importTaskService = importTaskService;
        this.importFailLogService = importFailLogService;
    }

    /**
     * 这个每一条数据解析都会来调用
     *
     * @param data
     * @param context
     */
    @Override
    public void invoke(ExcelVo data, AnalysisContext context) {
        Integer currentRowNum = context.readRowHolder().getRowIndex();
        log.info("invoke importId:{} rowNum:{} data:{}", importId, currentRowNum, JSON.toJSONString(data));
        //不处理示例数据
        if (currentRowNum < SAMPLE_LINES) {
            return;
        }
        list.add(data);
        //达到BATCH_COUNT，需要去处理一次数据，防止数据几万条数据在内存，容易OOM
        if (list.size() >= BATCH_COUNT) {
            dealData();
        }
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) {
        if (!(exception instanceof BusinessException)) {
            log.error("onException importId:{} error:{}", importId, ExceptionUtils.getStackTrace(exception));
            ImportTask fail = ImportTask.builder().id(importId).status(ImportStatusEnum.FAIL.getCode())
                    .detail("系统异常，请重新导入").build();
            importTaskService.update(fail);
            throw new BusinessException("系统异常，导入结束");
        } else {
            throw new BusinessException(exception.getMessage());
        }
    }

    /**
     * 所有数据解析完成了，都会来调用
     *
     * @param context
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("doAfterAllAnalysed importId:{} start", importId);
        //处理剩余数据
        dealData();
        //等待全部数据处理完毕
        int size = completableFutures.size();
        CompletableFuture[] arrays = completableFutures.toArray(new CompletableFuture[size]);
        CompletableFuture.allOf(arrays).join();
        //更新导入任务
        List<ImportFailLog> importFailLogs = importFailLogService.listByImportId(importId);
        int failSize = importFailLogs.size();
        String detail;
        if (failSize == 0) {
            detail = "总计导入数据" + totalCount + "条,成功导入数据" + totalCount + "条";
        } else {
            detail = "总计导入数据" + totalCount + "条,成功导入数据" + (totalCount - failSize) + "条,异常数据" + failSize + "条";
        }
        ImportTask build = ImportTask.builder().id(importId).status(ImportStatusEnum.SUCCESS.getCode())
                .failCount(failSize).detail(detail).needDownload(failSize != 0).build();
        importTaskService.update(build);
        log.info("doAfterAllAnalysed importId:{} end", importId);
    }

    /**
     * 这里会一行行的返回头
     *
     * @param headMap
     * @param context
     */
    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        log.info("invokeHead importId:{} head:{}", importId, JSONObject.toJSONString(headMap));
        //获取总条数
        ReadSheetHolder readSheetHolder = context.readSheetHolder();
        Integer totalRowNumber = readSheetHolder.getApproximateTotalRowNumber();
        this.totalCount = totalRowNumber <= SAMPLE_LINES ? 0 : totalRowNumber - SAMPLE_LINES;
        redisService.set(EasyExcelDealService.EXCEL_TOTAL_COUNT_PREFIX + importId, String.valueOf(totalCount), 60 * 30);
        log.info("invokeHeadMap importId:{} totalCount:{}", importId, totalCount);
        //校验
        String errorMsg = null;
        if (totalCount == 0) {
            errorMsg = "导入模板无数据";
        }
        if (totalCount > MAX_COUNT) {
            errorMsg = "导入数量超上限";
        }
        if (headMap.size() != 4) {
            errorMsg = "导入模版格式有误";
        }
        String name = headMap.get(0);
        String age = headMap.get(1);
        String tel = headMap.get(2);
        String introduction = headMap.get(3);
        if (!("姓名".equals(name) && "年龄".equals(age) && "电话".equals(tel) && "简介".equals(introduction))) {
            errorMsg = "导入模版格式有误";
        }
        if (StringUtils.isNotEmpty(errorMsg)) {
            log.info("invokeHeadMap importId:{} fail:{}", importId, errorMsg);
            ImportTask fail = ImportTask.builder().id(importId).status(ImportStatusEnum.FAIL.getCode())
                    .totalCount(totalCount).failCount(totalCount).detail(errorMsg).build();
            importTaskService.update(fail);
        } else {
            ImportTask importTask = ImportTask.builder().id(importId).totalCount(totalCount).build();
            importTaskService.update(importTask);
        }
        log.info("invokeHeadMap importId:{} end", importId);
    }

    /**
     * 处理数据
     */
    private void dealData() {
        log.info("dealExcelData importId:{}", importId);
        List<ExcelVo> lists = new ArrayList<>(list);
        list.clear();
        CompletableFuture<Void> completableFuture = CompletableFuture
                .runAsync(() -> easyExcelDealService.dealExcelData(lists, importId), taskExecutor);
        completableFutures.add(completableFuture);
    }
}