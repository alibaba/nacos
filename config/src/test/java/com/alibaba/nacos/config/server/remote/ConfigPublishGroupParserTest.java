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

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigPublishGroupParserTest {

    private ConfigPublishGroupParser configPublishGroupParser;

    @Before
    public void setUp() throws Exception {
        configPublishGroupParser = new ConfigPublishGroupParser();
    }

    @Test
    public void testParse() {
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setContent("content");
        configPublishRequest.setRequestId("requestId");
        configPublishRequest.setGroup("group");
        configPublishRequest.setCasMd5("md5");
        configPublishRequest.setDataId("dataId");
        configPublishRequest.setTenant("tenant");
        MonitorKey parse = configPublishGroupParser.parse(configPublishRequest);
        Assert.assertEquals("group", parse.getKey());
    }

}