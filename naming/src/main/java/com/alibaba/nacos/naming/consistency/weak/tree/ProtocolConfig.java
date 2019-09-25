package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author satjd
 */

@Component("protocolConfig")
public class ProtocolConfig {
    public static final String TREE_API_ON_PUB_PATH = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/tree/datum/onPub";

    public static final String TREE_API_ON_PUB_PATH_BATCH = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/tree/datum/onPub/batch";

    public static final String TREE_API_ON_DELETE_PATH = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/tree/datum/onDel";

    public static final String TREE_API_ON_DELETE_PATH_BATCH = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/tree/datum/onDel/batch";

    /** 分叉数 */
    @Value("${nacos.naming.tree.param.n:2}")
    private int treeParamN = 2;

    /** 时间戳之差大于这个值，视为过时消息，否则不能确定先后顺序 */
    @Value("${nacos.naming.tree.param.diffMax:0}")
    private long timestampDiffMax = 0;

    /** 处理本地消息变更的线程数 */
    @Value("${nacos.naming.tree.param.taskProcessorCnt:1}")
    private int taskProcessorCnt = 1;

    /** 是否启用聚合更新 */
    @Value("${nacos.naming.tree.param.batchUpdateEnabled:false}")
    private boolean batchUpdateEnabled;

    /** 一次传输的batch大小 */
    @Value("${nacos.naming.tree.param.transferTaskBatchSize:1000}")
    private int transferTaskBatchSize;

    /** 传输任务的调度间隔(ms)*/
    @Value("${nacos.naming.tree.param.transferTaskInterval:200}")
    private int transferTaskInterval;

    public int getTreeParamN() {
        return treeParamN;
    }

    public long getTimestampDiffMax() {
        return timestampDiffMax;
    }

    public int getTaskProcessorCnt() {
        return taskProcessorCnt;
    }

    public int getTransferTaskBatchSize() {
        return transferTaskBatchSize;
    }

    public int getTransferTaskInterval() {
        return transferTaskInterval;
    }

    public boolean isBatchUpdateEnabled() {
        return batchUpdateEnabled;
    }
}
