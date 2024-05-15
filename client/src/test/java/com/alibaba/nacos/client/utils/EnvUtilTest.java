/*
 *
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
 *
 */

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.api.common.Constants;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnvUtilTest {
    
    @Test
    void testSetSelfEnv() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.AMORY_TAG, Arrays.asList("a", "1"));
        headers.put(Constants.VIPSERVER_TAG, Arrays.asList("b", "2"));
        headers.put(Constants.LOCATION_TAG, Arrays.asList("c", "3"));
        EnvUtil.setSelfEnv(headers);
        assertEquals("a,1", EnvUtil.getSelfAmoryTag());
        assertEquals("b,2", EnvUtil.getSelfVipserverTag());
        assertEquals("c,3", EnvUtil.getSelfLocationTag());
        // reset by empty list
        headers.put(Constants.AMORY_TAG, Collections.emptyList());
        headers.put(Constants.VIPSERVER_TAG, Collections.emptyList());
        headers.put(Constants.LOCATION_TAG, Collections.emptyList());
        EnvUtil.setSelfEnv(headers);
        assertNull(EnvUtil.getSelfAmoryTag());
        assertNull(EnvUtil.getSelfVipserverTag());
        assertNull(EnvUtil.getSelfLocationTag());
    }
    
    @Test
    void testSetSelfEnv2() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.AMORY_TAG, Arrays.asList("a", "1"));
        headers.put(Constants.VIPSERVER_TAG, Arrays.asList("b", "2"));
        headers.put(Constants.LOCATION_TAG, Arrays.asList("c", "3"));
        EnvUtil.setSelfEnv(headers);
        assertEquals("a,1", EnvUtil.getSelfAmoryTag());
        assertEquals("b,2", EnvUtil.getSelfVipserverTag());
        assertEquals("c,3", EnvUtil.getSelfLocationTag());
        // reset
        headers.put(Constants.AMORY_TAG, null);
        headers.put(Constants.VIPSERVER_TAG, null);
        headers.put(Constants.LOCATION_TAG, null);
        EnvUtil.setSelfEnv(headers);
        assertNull(EnvUtil.getSelfAmoryTag());
        assertNull(EnvUtil.getSelfVipserverTag());
        assertNull(EnvUtil.getSelfLocationTag());
    }
}