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
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest.MetricsKey.CACHE_DATA;
import static com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest.MetricsKey.SNAPSHOT_DATA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigMetricRequestTest extends BasedConfigRequestTest {
    
    @Override
    @Test
    public void testSerialize() throws JsonProcessingException {
        ClientConfigMetricRequest clientMetrics = new ClientConfigMetricRequest();
        clientMetrics.putAllHeader(HEADERS);
        clientMetrics.getMetricsKeys()
                .add(ClientConfigMetricRequest.MetricsKey.build(CACHE_DATA, String.join("+", KEY)));
        clientMetrics.getMetricsKeys()
                .add(ClientConfigMetricRequest.MetricsKey.build(SNAPSHOT_DATA, String.join("+", KEY)));
        final String requestId = injectRequestUuId(clientMetrics);
        String json = mapper.writeValueAsString(clientMetrics);
        assertTrue(json.contains("\"type\":\"" + "cacheData" + "\""));
        assertTrue(json.contains("\"type\":\"" + "snapshotData" + "\""));
        assertTrue(json.contains("\"key\":\"" + String.join("+", KEY) + "\""));
        assertTrue(json.contains("\"module\":\"" + Constants.Config.CONFIG_MODULE));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json =
                "{\"headers\":{\"header1\":\"test_header1\"}," + "\"metricsKeys\":[{\"type\":\"cacheData\",\"key\":"
                        + "\"test_data+group+test_tenant\"},{\"type\":\"snapshotData\","
                        + "\"key\":\"test_data+group+test_tenant\"}],\"module\":\"config\"}";
        ClientConfigMetricRequest actual = mapper.readValue(json, ClientConfigMetricRequest.class);
        assertEquals(2, actual.getMetricsKeys().size());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getModule());
        assertEquals(HEADER_VALUE, actual.getHeader(HEADER_KEY));
    }
    
    @Test
    void testMetricsKeysEquals() {
        String dataKey = String.join("+", KEY);
        ClientConfigMetricRequest.MetricsKey key = ClientConfigMetricRequest.MetricsKey.build(CACHE_DATA, dataKey);
        assertEquals(key, key);
        assertNotEquals(null, key);
        assertNotEquals(key, new ClientConfigMetricRequest());
        ClientConfigMetricRequest.MetricsKey newOne = ClientConfigMetricRequest.MetricsKey.build(SNAPSHOT_DATA,
                dataKey);
        assertNotEquals(key, newOne);
        newOne.setType(CACHE_DATA);
        assertEquals(key, newOne);
    }
    
    @Test
    void testMetricsHashCode() {
        String dataKey = String.join("+", KEY);
        ClientConfigMetricRequest.MetricsKey key = ClientConfigMetricRequest.MetricsKey.build(CACHE_DATA, dataKey);
        assertEquals(Objects.hash(CACHE_DATA, dataKey), key.hashCode());
    }
    
    @Test
    void testMetricsToString() {
        ClientConfigMetricRequest.MetricsKey key = ClientConfigMetricRequest.MetricsKey.build(CACHE_DATA,
                String.join("+", KEY));
        assertEquals("MetricsKey{type='cacheData', key='test_data+group+test_tenant'}", key.toString());
    }
}
