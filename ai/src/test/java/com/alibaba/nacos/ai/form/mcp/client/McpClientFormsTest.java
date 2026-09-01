/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.form.mcp.client;

import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.request.QueryMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientFormsTest {
    
    @Test
    void releaseParsesCompleteFormAndDefaultsToDirectOnline() throws Exception {
        McpReleaseForm form = releaseForm();
        form.setToolSpecification("{\"tools\":[]}");
        form.setResourceSpecification("{\"resources\":[{\"uri\":\"file:///a\"}]}");
        form.setEndpointSpecification(
            "{\"type\":\"DIRECT\",\"data\":{\"address\":\"127.0.0.1\",\"port\":\"8080\"}}");
        
        ReleaseMcpServerRequest request = form.toRequest();
        
        assertEquals("public", request.getNamespaceId());
        assertEquals("demo", request.getMcpName());
        assertEquals("1.0.0", request.getServerSpecification().getVersionDetail().getVersion());
        assertNotNull(request.getToolSpecification());
        assertEquals("file:///a",
            request.getResourceSpecification().getResources().get(0).get("uri"));
        assertEquals("DIRECT", request.getEndpointSpecification().getType());
        assertFalse(request.isCreateDraft());
    }
    
    @Test
    void releaseAcceptsExplicitDraftAndOmittedOptionalSpecifications() throws Exception {
        McpReleaseForm form = releaseForm();
        form.setCreateDraft("TrUe");
        
        ReleaseMcpServerRequest request = form.toRequest();
        
        assertTrue(request.isCreateDraft());
        assertNull(request.getToolSpecification());
        assertNull(request.getResourceSpecification());
        assertNull(request.getEndpointSpecification());
    }
    
    @Test
    void releaseRejectsMissingMismatchMalformedJsonAndInvalidBoolean() {
        McpReleaseForm form = new McpReleaseForm();
        assertDetail(ErrorCode.PARAMETER_MISSING,
            assertThrows(NacosApiException.class, form::toRequest));
        
        form = releaseForm();
        form.setMcpName("other");
        assertDetail(ErrorCode.PARAMETER_VALIDATE_ERROR,
            assertThrows(NacosApiException.class, form::toRequest));
        
        form = releaseForm();
        form.setToolSpecification("{");
        assertDetail(ErrorCode.PARAMETER_VALIDATE_ERROR,
            assertThrows(NacosApiException.class, form::toRequest));
        
        form = releaseForm();
        form.setCreateDraft("yes");
        assertDetail(ErrorCode.PARAMETER_VALIDATE_ERROR,
            assertThrows(NacosApiException.class, form::toRequest));
    }
    
    @Test
    void queryAndEndpointFormsNormalizeNamespaceAndPreserveValues() throws Exception {
        McpQueryForm queryForm = new McpQueryForm();
        queryForm.setMcpName("demo");
        queryForm.setVersion("1.0.0");
        QueryMcpServerRequest query = queryForm.toRequest();
        assertEquals("public", query.getNamespaceId());
        assertEquals("demo", query.getMcpName());
        assertEquals("1.0.0", query.getVersion());
        
        McpEndpointForm endpointForm = new McpEndpointForm();
        endpointForm.setMcpName("demo");
        endpointForm.setAddress("127.0.0.1");
        endpointForm.setPort(8080);
        endpointForm.setVersion("1.0.0");
        McpServerEndpointRequest endpoint = endpointForm.toRequest("registerEndpoint");
        assertEquals("public", endpoint.getNamespaceId());
        assertEquals("registerEndpoint", endpoint.getType());
        assertEquals("127.0.0.1", endpoint.getAddress());
        assertEquals(8080, endpoint.getPort());
        assertEquals("1.0.0", endpoint.getVersion());
    }
    
    @Test
    void queryAndEndpointRequireMcpName() {
        assertEquals(NacosException.INVALID_PARAM,
            assertThrows(NacosApiException.class, () -> new McpQueryForm().toRequest())
                .getErrCode());
        assertEquals(NacosException.INVALID_PARAM,
            assertThrows(NacosApiException.class,
                () -> new McpEndpointForm().toRequest("registerEndpoint")).getErrCode());
    }
    
    private McpReleaseForm releaseForm() {
        McpReleaseForm result = new McpReleaseForm();
        result.setMcpName("demo");
        result.setServerSpecification(
            "{\"name\":\"demo\",\"protocol\":\"mcp-sse\","
                + "\"versionDetail\":{\"version\":\"1.0.0\",\"is_latest\":true}}");
        return result;
    }
    
    private void assertDetail(ErrorCode expected, NacosApiException actual) {
        assertEquals(expected.getCode(), actual.getDetailErrCode());
    }
}
