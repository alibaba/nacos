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

package com.alibaba.nacos.naming.healthcheck;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsInfoTest {
    
    @Test
    void testAccessorsAndToString() {
        RsInfo rsInfo = new RsInfo();
        rsInfo.setAk("ak");
        rsInfo.setServiceName("service");
        rsInfo.setCluster("cluster");
        rsInfo.setIp("1.1.1.1");
        rsInfo.setPort(8848);
        rsInfo.setLoad(1.0D);
        rsInfo.setCpu(2.0D);
        rsInfo.setRt(3.0D);
        rsInfo.setQps(4.0D);
        rsInfo.setMem(5.0D);
        rsInfo.setWeight(6.0D);
        rsInfo.setEphemeral(false);
        rsInfo.setMetadata(Collections.singletonMap("k", "v"));
        
        assertEquals("ak", rsInfo.getAk());
        assertEquals("service", rsInfo.getServiceName());
        assertEquals("cluster", rsInfo.getCluster());
        assertEquals("1.1.1.1", rsInfo.getIp());
        assertEquals(8848, rsInfo.getPort());
        assertEquals(1.0D, rsInfo.getLoad());
        assertEquals(2.0D, rsInfo.getCpu());
        assertEquals(3.0D, rsInfo.getRt());
        assertEquals(4.0D, rsInfo.getQps());
        assertEquals(5.0D, rsInfo.getMem());
        assertEquals(6.0D, rsInfo.getWeight());
        assertFalse(rsInfo.isEphemeral());
        assertEquals(Collections.singletonMap("k", "v"), rsInfo.getMetadata());
        assertTrue(rsInfo.toString().contains("\"ak\":\"ak\""));
    }
}
