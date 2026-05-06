/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class HealthRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    HealthRemoteHandler healthRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithNaming();
        healthRemoteHandler = new HealthRemoteHandler(clientHolder);
    }
    
    @Test
    void checkReadiness() throws NacosException {
        when(namingMaintainerService.readiness()).thenReturn(true, false);
        Result<String> result = healthRemoteHandler.checkReadiness();
        assertEquals("ok", result.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        result = healthRemoteHandler.checkReadiness();
        assertEquals("Nacos server readiness failed.", result.getMessage());
        assertEquals(ErrorCode.SERVER_ERROR.getCode(), result.getCode());
    }
}