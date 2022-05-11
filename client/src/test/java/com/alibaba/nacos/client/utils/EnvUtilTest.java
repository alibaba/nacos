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
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnvUtilTest {
    
    @Test
    public void testSetSelfEnv() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.AMORY_TAG, Arrays.asList("a", "1"));
        headers.put(Constants.VIPSERVER_TAG, Arrays.asList("b", "2"));
        headers.put(Constants.LOCATION_TAG, Arrays.asList("c", "3"));
        EnvUtil.setSelfEnv(headers);
        Assert.assertEquals("a,1", EnvUtil.getSelfAmoryTag());
        Assert.assertEquals("b,2", EnvUtil.getSelfVipserverTag());
        Assert.assertEquals("c,3", EnvUtil.getSelfLocationTag());
    }
}