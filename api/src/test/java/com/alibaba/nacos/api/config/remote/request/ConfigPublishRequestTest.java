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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPublishRequestTest extends BasedConfigRequestTest {
    
    private static final String TAG_PARAM = "tag";
    
    private static final String APP_NAME_PARAM = "appName";
    
    ConfigPublishRequest configPublishRequest;
    
    String requestId;
    
    @BeforeEach
    void before() {
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
        assertEquals(DATA_ID, actual.getDataId());
        assertEquals(GROUP, actual.getGroup());
        assertEquals(TENANT, actual.getTenant());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getModule());
        assertEquals(CONTENT, actual.getContent());
        assertEquals(MD5, actual.getCasMd5());
        assertEquals(TAG_PARAM, actual.getAdditionParam(TAG_PARAM));
        assertEquals(APP_NAME_PARAM, actual.getAdditionParam(APP_NAME_PARAM));
    }
}
