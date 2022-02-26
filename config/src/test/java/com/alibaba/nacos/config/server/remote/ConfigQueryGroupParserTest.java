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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConfigQueryGroupParserTest {

    private ConfigQueryGroupParser configQueryGroupParser;

    @Before
    public void setUp() throws Exception {
        configQueryGroupParser = new ConfigQueryGroupParser();
    }

    @Test
    public void testParse() {
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setRequestId("requestId");
        configQueryRequest.setGroup("group");
        configQueryRequest.setDataId("dataId");
        configQueryRequest.setTenant("tenant");
        MonitorKey parse = configQueryGroupParser.parse(configQueryRequest);
        Assert.assertEquals("group", parse.getKey());
    }
}