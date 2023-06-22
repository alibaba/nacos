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

package com.alibaba.nacos.naming.cluster.transport;

import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JacksonSerializerTest {
    
    private Serializer serializer;
    
    private SwitchDomain switchDomain;
    
    @Before
    public void setUp() throws Exception {
        serializer = new JacksonSerializer();
        switchDomain = new SwitchDomain();
    }
    
    @Test
    public void testSerialize() {
        String actual = new String(serializer.serialize(switchDomain));
        assertTrue(actual.contains("\"defaultPushCacheMillis\":10000"));
        assertTrue(actual.contains("\"clientBeatInterval\":5000"));
        assertTrue(actual.contains("\"defaultCacheMillis\":3000"));
        assertTrue(actual.contains("\"distroEnabled\":true"));
    }
    
    @Test
    @SuppressWarnings("checkstyle:linelength")
    public void testDeserialize() {
        String example = "{\"adWeightMap\":{},\"defaultPushCacheMillis\":10000,\"clientBeatInterval\":5000,\"defaultCacheMillis\":3000,\"distroThreshold\":0.7,\"healthCheckEnabled\":true,\"autoChangeHealthCheckEnabled\":true,\"distroEnabled\":true,\"enableStandalone\":true,\"pushEnabled\":true,\"checkTimes\":3,\"httpHealthParams\":{\"max\":5000,\"min\":500,\"factor\":0.85},\"tcpHealthParams\":{\"max\":5000,\"min\":1000,\"factor\":0.75},\"mysqlHealthParams\":{\"max\":3000,\"min\":2000,\"factor\":0.65},\"incrementalList\":[],\"serverStatusSynchronizationPeriodMillis\":2000,\"serviceStatusSynchronizationPeriodMillis\":5000,\"disableAddIP\":false,\"sendBeatOnly\":false,\"lightBeatEnabled\":true,\"doubleWriteEnabled\":true,\"limitedUrlMap\":{},\"distroServerExpiredMillis\":10000,\"pushGoVersion\":\"0.1.0\",\"pushJavaVersion\":\"0.1.0\",\"pushPythonVersion\":\"0.4.3\",\"pushCVersion\":\"1.0.12\",\"pushCSharpVersion\":\"0.9.0\",\"enableAuthentication\":false,\"defaultInstanceEphemeral\":true,\"healthCheckWhiteList\":[],\"name\":\"00-00---000-NACOS_SWITCH_DOMAIN-000---00-00\"}";
        SwitchDomain actual = serializer.deserialize(ByteUtils.toBytes(example), SwitchDomain.class);
        assertEquals(10000, actual.getDefaultPushCacheMillis());
        assertEquals(5000, actual.getClientBeatInterval());
        assertEquals(3000, actual.getDefaultCacheMillis());
        assertTrue(actual.isDistroEnabled());
    }
}
