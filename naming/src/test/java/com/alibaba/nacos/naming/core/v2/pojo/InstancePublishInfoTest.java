/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.pojo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InstancePublishInfoTest {

    @Test
    void testEqualsWithSameCluster() {
        InstancePublishInfo info1 = new InstancePublishInfo("1.1.1.1", 8080);
        info1.setCluster("clusterA");
        InstancePublishInfo info2 = new InstancePublishInfo("1.1.1.1", 8080);
        info2.setCluster("clusterA");
        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    void testNotEqualsWithDifferentCluster() {
        InstancePublishInfo info1 = new InstancePublishInfo("1.1.1.1", 8080);
        info1.setCluster("clusterA");
        InstancePublishInfo info2 = new InstancePublishInfo("1.1.1.1", 8080);
        info2.setCluster("clusterB");
        assertNotEquals(info1, info2);
    }

    @Test
    void testEqualsWithNullCluster() {
        InstancePublishInfo info1 = new InstancePublishInfo("1.1.1.1", 8080);
        InstancePublishInfo info2 = new InstancePublishInfo("1.1.1.1", 8080);
        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    void testNotEqualsWithDifferentPort() {
        InstancePublishInfo info1 = new InstancePublishInfo("1.1.1.1", 8080);
        InstancePublishInfo info2 = new InstancePublishInfo("1.1.1.1", 8081);
        assertNotEquals(info1, info2);
    }

    @Test
    void testNotEqualsWithDifferentHealthy() {
        InstancePublishInfo info1 = new InstancePublishInfo("1.1.1.1", 8080);
        info1.setHealthy(true);
        InstancePublishInfo info2 = new InstancePublishInfo("1.1.1.1", 8080);
        info2.setHealthy(false);
        assertNotEquals(info1, info2);
    }

    @Test
    void testEqualsSameObject() {
        InstancePublishInfo info = new InstancePublishInfo("1.1.1.1", 8080);
        assertEquals(info, info);
    }

    @Test
    void testNotEqualsNull() {
        InstancePublishInfo info = new InstancePublishInfo("1.1.1.1", 8080);
        assertNotEquals(null, info);
    }

    @Test
    void testGetMetadataId() {
        InstancePublishInfo info = new InstancePublishInfo("1.1.1.1", 8080);
        info.setCluster("clusterA");
        assertEquals("1.1.1.1:8080:clusterA", info.getMetadataId());
    }
}
