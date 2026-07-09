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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.service.ard.ArdSearchService;
import com.alibaba.nacos.api.ai.model.ard.ArdCatalog;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.annotation.Secured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdWellKnownController}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdWellKnownControllerTest {
    
    @Mock
    private ArdSearchService ardSearchService;
    
    @AfterEach
    void tearDown() {
        System.clearProperty(ArdWellKnownController.KEY_WELL_KNOWN_ENABLED);
        System.clearProperty(ArdWellKnownController.KEY_WELL_KNOWN_NAMESPACE_ID);
    }
    
    @Test
    void catalogShouldReturnPublicCatalogWhenEnabled() throws NacosException {
        System.setProperty(ArdWellKnownController.KEY_WELL_KNOWN_ENABLED, "true");
        ArdCatalog catalog = new ArdCatalog();
        when(ardSearchService.catalog(com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID))
            .thenReturn(catalog);
        
        assertSame(catalog, controller().catalog());
    }
    
    @Test
    void catalogShouldUseConfiguredNamespaceWhenEnabled() throws NacosException {
        System.setProperty(ArdWellKnownController.KEY_WELL_KNOWN_ENABLED, "true");
        System.setProperty(ArdWellKnownController.KEY_WELL_KNOWN_NAMESPACE_ID, "tenant-a");
        ArdCatalog catalog = new ArdCatalog();
        when(ardSearchService.catalog("tenant-a")).thenReturn(catalog);
        
        assertSame(catalog, controller().catalog());
    }
    
    @Test
    void catalogShouldReturnNotFoundWhenDisabled() {
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> controller().catalog());
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getDetailErrCode());
    }
    
    @Test
    void catalogShouldBeAnonymousDiscoveryEndpoint() throws NoSuchMethodException {
        Method method = ArdWellKnownController.class.getMethod("catalog");
        
        assertFalse(method.isAnnotationPresent(Secured.class));
    }
    
    private ArdWellKnownController controller() {
        return new ArdWellKnownController(ardSearchService);
    }
}
