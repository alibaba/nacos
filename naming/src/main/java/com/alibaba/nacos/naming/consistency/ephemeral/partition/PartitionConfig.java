package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import org.springframework.beans.factory.annotation.Value;

/**
 * Stores some configurations for Partition protocol
 *
 * @author nkorange
 * @since 1.0.0
 */
public class PartitionConfig {

    @Value("taskDispatchThreadCount")
    private int taskDispatchThreadCount = 10;

    @Value("taskDispatchPeriod")
    private int taskDispatchPeriod = 2000;

    @Value("batchSyncKeyCount")
    private int batchSyncKeyCount = 1000;

    public int getTaskDispatchThreadCount() {
        return taskDispatchThreadCount;
    }

    public int getTaskDispatchPeriod() {
        return taskDispatchPeriod;
    }

    public int getBatchSyncKeyCount() {
        return batchSyncKeyCount;
    }
}
