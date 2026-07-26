/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PropertiesConstantTest {
    
    @Test
    void testConstructorAndConstants() {
        assertNotNull(new PropertiesConstant());
        assertEquals("notifyConnectTimeout", PropertiesConstant.NOTIFY_CONNECT_TIMEOUT);
        assertEquals("notifySocketTimeout", PropertiesConstant.NOTIFY_SOCKET_TIMEOUT);
        assertEquals("isHealthCheck", PropertiesConstant.IS_HEALTH_CHECK);
        assertEquals("maxHealthCheckFailCount", PropertiesConstant.MAX_HEALTH_CHECK_FAIL_COUNT);
        assertEquals("maxContent", PropertiesConstant.MAX_CONTENT);
        assertEquals("isManageCapacity", PropertiesConstant.IS_MANAGE_CAPACITY);
        assertEquals("isCapacityLimitCheck", PropertiesConstant.IS_CAPACITY_LIMIT_CHECK);
        assertEquals("defaultClusterQuota", PropertiesConstant.DEFAULT_CLUSTER_QUOTA);
        assertEquals("defaultGroupQuota", PropertiesConstant.DEFAULT_GROUP_QUOTA);
        assertEquals("defaultTenantQuota", PropertiesConstant.DEFAULT_TENANT_QUOTA);
        assertEquals("defaultMaxSize", PropertiesConstant.DEFAULT_MAX_SIZE);
        assertEquals("defaultMaxAggrCount", PropertiesConstant.DEFAULT_MAX_AGGR_COUNT);
        assertEquals("defaultMaxAggrSize", PropertiesConstant.DEFAULT_MAX_AGGR_SIZE);
        assertEquals("correctUsageDelay", PropertiesConstant.CORRECT_USAGE_DELAY);
        assertEquals("initialExpansionPercent", PropertiesConstant.INITIAL_EXPANSION_PERCENT);
        assertEquals("nacos.config.search.max_capacity", PropertiesConstant.SEARCH_MAX_CAPACITY);
        assertEquals("nacos.config.search.max_thread", PropertiesConstant.SEARCH_MAX_THREAD);
        assertEquals("nacos.config.search.wait_timeout", PropertiesConstant.SEARCH_WAIT_TIMEOUT);
        assertEquals("dumpChangeOn", PropertiesConstant.DUMP_CHANGE_ON);
        assertEquals("dumpChangeWorkerInterval", PropertiesConstant.DUMP_CHANGE_WORKER_INTERVAL);
        assertEquals("nacos.config.retention.days", PropertiesConstant.CONFIG_RENTENTION_DAYS);
    }
}
