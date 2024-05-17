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

package com.alibaba.nacos.common.http.param;

import com.alibaba.nacos.api.naming.CommonParams;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryTest {
    
    @Test
    void testInitParams() {
        Map<String, String> parameters = new LinkedHashMap<String, String>();
        parameters.put(CommonParams.NAMESPACE_ID, "namespace");
        parameters.put(CommonParams.SERVICE_NAME, "service");
        parameters.put(CommonParams.GROUP_NAME, "group");
        parameters.put(CommonParams.CLUSTER_NAME, null);
        parameters.put("ip", "1.1.1.1");
        parameters.put("port", String.valueOf(9999));
        parameters.put("weight", String.valueOf(1.0));
        parameters.put("ephemeral", String.valueOf(true));
        String excepted = "namespaceId=namespace&serviceName=service&groupName=group&ip=1.1.1.1&port=9999&weight=1.0&ephemeral=true";
        Query actual = Query.newInstance().initParams(parameters);
        assertEquals(excepted, actual.toQueryUrl());
        assertEquals("namespace", actual.getValue(CommonParams.NAMESPACE_ID));
    }
    
    @Test
    void testAddParams() throws Exception {
        Query query = Query.newInstance().addParam("key-1", "value-1").addParam("key-2", "value-2");
        String s1 = query.toQueryUrl();
        String s2 =
                "key-1=" + URLEncoder.encode("value-1", StandardCharsets.UTF_8.name()) + "&key-2=" + URLEncoder.encode("value-2",
                        StandardCharsets.UTF_8.name());
        assertEquals(s2, s1);
        assertEquals("value-1", query.getValue("key-1"));
    }
    
    @Test
    void testClear() {
        Query query = Query.newInstance().addParam("key-1", "value-1").addParam("key-2", "value-2");
        assertFalse(query.isEmpty());
        assertEquals("value-1", query.getValue("key-1"));
        query.clear();
        assertTrue(query.isEmpty());
    }
}
