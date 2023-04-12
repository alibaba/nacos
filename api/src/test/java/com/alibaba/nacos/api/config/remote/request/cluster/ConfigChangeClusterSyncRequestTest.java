package com.alibaba.nacos.api.config.remote.request.cluster;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.remote.request.BasedConfigRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigChangeClusterSyncRequestTest extends BasedConfigRequestTest {
    
    ConfigChangeClusterSyncRequest configChangeClusterSyncRequest;
    
    String requestId;
    
    @Before
    public void before() {
        configChangeClusterSyncRequest = new ConfigChangeClusterSyncRequest();
        configChangeClusterSyncRequest.setDataId(DATA_ID);
        configChangeClusterSyncRequest.setGroup(GROUP);
        configChangeClusterSyncRequest.setTenant(TENANT);
        configChangeClusterSyncRequest.setTag(TAG);
        configChangeClusterSyncRequest.setBeta(Boolean.TRUE);
        configChangeClusterSyncRequest.setLastModified(0L);
        configChangeClusterSyncRequest.putAllHeader(HEADERS);
        requestId = injectRequestUuId(configChangeClusterSyncRequest);
    }
    
    @Override
    @Test
    public void testSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(configChangeClusterSyncRequest);
        System.out.println(json);
        assertTrue(json.contains("\"module\":\"" + Constants.Config.CONFIG_MODULE));
        assertTrue(json.contains("\"dataId\":\"" + DATA_ID));
        assertTrue(json.contains("\"group\":\"" + GROUP));
        assertTrue(json.contains("\"tenant\":\"" + TENANT));
        assertTrue(json.contains("\"tag\":\"" + TAG));
        assertTrue(json.contains("\"beta\":" + Boolean.TRUE));
        assertTrue(json.contains("\"requestId\":\"" + requestId));
        assertTrue(json.contains("\"lastModified\":" + 0));
        
    }
    
    @Override
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"headers\":{\"header1\":\"test_header1\"},\"requestId\":\"ece89111-3c42-4055-aca4-c95e16ec564b\",\"dataId\":\"test_data\","
                + "\"group\":\"group\",\"tenant\":\"test_tenant\","
                + "\"tag\":\"tag\",\"lastModified\":0,\"beta\":true,\"module\":\"config\"}";
        ConfigChangeClusterSyncRequest actual = mapper.readValue(json, ConfigChangeClusterSyncRequest.class);
        assertEquals(actual.getDataId(), DATA_ID);
        assertEquals(actual.getGroup(), GROUP);
        assertEquals(actual.getTenant(), TENANT);
        assertEquals(actual.getModule(), Constants.Config.CONFIG_MODULE);
        assertEquals(actual.getLastModified(), 0L);
        assertTrue(actual.isBeta());
    }
}
