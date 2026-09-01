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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationControlStore.VersionedValue;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2aMigrationControlStoreTest {
    
    private ConfigQueryChainService queryService;
    
    private ConfigOperationService operationService;
    
    private A2aMigrationControlStore store;
    
    @BeforeEach
    void setUp() {
        queryService = mock(ConfigQueryChainService.class);
        operationService = mock(ConfigOperationService.class);
        store = new A2aMigrationControlStore(queryService, operationService);
    }
    
    @Test
    void shouldReadMarkerAndLeaseWithMd5() {
        A2aMigrationMarker marker = A2aMigrationMarker.syncing("g", false, 10L);
        when(queryService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(found(JacksonUtils.toJson(marker), "marker-md5"));
        VersionedValue<A2aMigrationMarker> markerValue = store.readMarker();
        assertEquals("g", markerValue.getValue().getGeneration());
        assertEquals("marker-md5", markerValue.getMd5());
        ArgumentCaptor<ConfigQueryChainRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigQueryChainRequest.class);
        verify(queryService).handle(requestCaptor.capture());
        assertEquals(A2aMigrationControlStore.MIGRATION_MARKER_DATA_ID,
            requestCaptor.getValue().getDataId());
        assertEquals(A2aMigrationControlStore.INTERNAL_GROUP,
            requestCaptor.getValue().getGroup());
        assertEquals(Constants.DEFAULT_NAMESPACE_ID,
            requestCaptor.getValue().getTenant());
        
        A2aMigrationLeaseRecord lease = A2aMigrationLeaseRecord.of("owner", 20L);
        when(queryService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(found(JacksonUtils.toJson(lease), "lease-md5"));
        VersionedValue<A2aMigrationLeaseRecord> leaseValue = store.readLease();
        assertEquals("owner", leaseValue.getValue().getOwner());
        assertEquals("lease-md5", leaseValue.getMd5());
    }
    
    @Test
    void absentControlObjectShouldReturnNull() {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(queryService.handle(any(ConfigQueryChainRequest.class))).thenReturn(null, response);
        assertNull(store.readMarker());
        assertNull(store.readLease());
    }
    
    @Test
    void unavailableOrInvalidControlObjectShouldFailClosed() {
        ConfigQueryChainResponse gray = found("{}", "md5");
        gray.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY);
        ConfigQueryChainResponse empty = found(" ", "md5");
        ConfigQueryChainResponse noMd5 = found("{}", " ");
        ConfigQueryChainResponse nullValue = found("null", "md5");
        ConfigQueryChainResponse invalid = found("not-json", "md5");
        when(queryService.handle(any(ConfigQueryChainRequest.class))).thenReturn(gray, empty,
            noMd5, nullValue, invalid);
        assertThrows(IllegalStateException.class, store::readMarker);
        assertThrows(IllegalStateException.class, store::readMarker);
        assertThrows(IllegalStateException.class, store::readMarker);
        assertThrows(IllegalStateException.class, store::readMarker);
        assertThrows(RuntimeException.class, store::readMarker);
    }
    
    @Test
    void shouldWriteCreateCasAndBoundedProgressForms() throws Exception {
        when(operationService.publishConfig(any(ConfigForm.class),
            any(ConfigRequestInfo.class), isNull())).thenReturn(true, true, true, true, false);
        A2aMigrationMarker marker = A2aMigrationMarker.syncing("g", true, 10L);
        assertTrue(store.createMarker(marker));
        assertTrue(store.compareAndSetMarker(marker, "marker-md5"));
        A2aMigrationLeaseRecord lease = A2aMigrationLeaseRecord.of("owner", 20L);
        assertTrue(store.createLease(lease));
        assertTrue(store.compareAndSetLease(lease, "lease-md5"));
        A2aMigrationProgress progress = new A2aMigrationProgress();
        progress.setCursor("c".repeat(600));
        assertFalse(store.saveProgress(progress));
        
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        ArgumentCaptor<ConfigRequestInfo> infoCaptor =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(operationService, org.mockito.Mockito.times(5)).publishConfig(
            formCaptor.capture(), infoCaptor.capture(), isNull());
        assertForm(formCaptor.getAllValues().get(0),
            A2aMigrationControlStore.MIGRATION_MARKER_DATA_ID);
        assertEquals(Boolean.FALSE, infoCaptor.getAllValues().get(0).getUpdateForExist());
        assertNull(infoCaptor.getAllValues().get(0).getCasMd5());
        assertEquals(Boolean.TRUE, infoCaptor.getAllValues().get(1).getUpdateForExist());
        assertEquals("marker-md5", infoCaptor.getAllValues().get(1).getCasMd5());
        assertForm(formCaptor.getAllValues().get(2),
            A2aMigrationControlStore.RECONCILIATION_LEASE_DATA_ID);
        assertEquals(Boolean.FALSE, infoCaptor.getAllValues().get(2).getUpdateForExist());
        assertEquals("lease-md5", infoCaptor.getAllValues().get(3).getCasMd5());
        assertForm(formCaptor.getAllValues().get(4),
            A2aMigrationControlStore.RECONCILIATION_PROGRESS_DATA_ID);
        A2aMigrationProgress persisted = JacksonUtils.toObj(
            formCaptor.getAllValues().get(4).getContent(), A2aMigrationProgress.class);
        assertEquals(512, persisted.getCursor().length());
    }
    
    private ConfigQueryChainResponse found(String content, String md5) {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        result.setContent(content);
        result.setMd5(md5);
        return result;
    }
    
    private void assertForm(ConfigForm form, String dataId) {
        assertEquals(dataId, form.getDataId());
        assertEquals(A2aMigrationControlStore.INTERNAL_GROUP, form.getGroup());
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals(ConfigType.JSON.getType(), form.getType());
        assertEquals("nacos", form.getSrcUser());
    }
}
