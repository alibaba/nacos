/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config.remote.request;

import com.alibaba.nacos.api.common.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigPublishRequestTest extends BasedConfigRequestTest {
    
    ConfigPublishRequest configPublishRequest;
    
    private static final String TAG_PARAM = "tag";
    
    private static final String APP_NAME_PARAM = "appName";
    
    String requestId;
    
    @Before
    public void before() {
        configPublishRequest = new ConfigPublishRequest(DATA_ID, GROUP, TENANT, CONTENT);
        configPublishRequest.putAdditionalParam(TAG_PARAM, TAG_PARAM);
        configPublishRequest.putAdditionalParam(APP_NAME_PARAM, APP_NAME_PARAM);
        configPublishRequest.setCasMd5(MD5);
        configPublishRequest.putAllHeader(HEADERS);
        requestId = injectRequestUuId(configPublishRequest);
    }
    
    @Override
    @Test
    public void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(configPublishRequest);
        assertTrue(json.contains("\"module\":\"" + Constants.Config.CONFIG_MODULE));
        assertTrue(json.contains("\"dataId\":\"" + DATA_ID));
        assertTrue(json.contains("\"group\":\"" + GROUP));
        assertTrue(json.contains("\"tenant\":\"" + TENANT));
        assertTrue(json.contains("\"content\":\"" + CONTENT));
        assertTrue(json.contains("\"casMd5\":\"" + MD5));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{\"header1\":\"test_header1\"},\"dataId\":\"test_data\",\"group\":\"group\","
                + "\"tenant\":\"test_tenant\",\"content\":\"content\",\"casMd5\":\"test_MD5\","
                + "\"additionMap\":{\"appName\":\"appName\",\"tag\":\"tag\"},\"module\":\"config\"}";
        ConfigPublishRequest actual = mapper.readValue(json, ConfigPublishRequest.class);
        assertEquals(actual.getDataId(), DATA_ID);
        assertEquals(actual.getGroup(), GROUP);
        assertEquals(actual.getTenant(), TENANT);
        assertEquals(actual.getModule(), Constants.Config.CONFIG_MODULE);
        assertEquals(actual.getContent(), CONTENT);
        assertEquals(actual.getCasMd5(), MD5);
        assertEquals(actual.getAdditionParam(TAG_PARAM), TAG_PARAM);
        assertEquals(actual.getAdditionParam(APP_NAME_PARAM), APP_NAME_PARAM);
    }
}
