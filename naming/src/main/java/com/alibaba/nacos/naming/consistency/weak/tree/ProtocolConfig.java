/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    /**
     * 分叉数
     */
    @Value("${nacos.naming.tree.param.n:2}")
    private int treeParamN = 2;

    /**
     * 时间戳之差大于这个值，视为过时消息，否则不能确定先后顺序
     */
    @Value("${nacos.naming.tree.param.diffMax:0}")
    private long timestampDiffMax = 0;

    /**
     * 处理本地消息变更的线程数
     */
    @Value("${nacos.naming.tree.param.taskProcessorCnt:1}")
    private int taskProcessorCnt = 1;

    /**
     * 是否启用聚合更新
     */
    @Value("${nacos.naming.tree.param.batchUpdateEnabled:false}")
    private boolean batchUpdateEnabled;

    /**
     * 一次传输的batch大小
     */
    @Value("${nacos.naming.tree.param.transferTaskBatchSize:1000}")
    private int transferTaskBatchSize;

    /**
     * 传输任务的调度间隔(ms)
     */
    @Value("${nacos.naming.tree.param.transferTaskInterval:200}")
    private int transferTaskInterval;

    /**
     * 传输任务的调度间隔(ms)
     */
    @Value("${nacos.naming.tree.bolt.port:8990}")
    private int boltPort;

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

    public int getBoltPort() {
        return boltPort;
    }
}
