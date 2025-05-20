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

package com.alibaba.nacos.console.handler.impl.inner;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.core.cluster.health.AbstractModuleHealthChecker;
import com.alibaba.nacos.core.cluster.health.ModuleHealthCheckerHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthInnerHandlerTest {
    
    @Mock
    AbstractModuleHealthChecker moduleHealthChecker;
    
    HealthInnerHandler healthInnerHandler;
    
    @BeforeEach
    void setUp() {
        healthInnerHandler = new HealthInnerHandler();
        ModuleHealthCheckerHolder.getInstance().registerChecker(moduleHealthChecker);
    }
    
    @AfterEach
    void tearDown() {
        ((List<AbstractModuleHealthChecker>) ReflectionTestUtils.getField(ModuleHealthCheckerHolder.getInstance(),
                "moduleHealthCheckers")).remove(moduleHealthChecker);
    }
    
    @Test
    void checkReadinessSuccess() {
        when(moduleHealthChecker.readiness()).thenReturn(true);
        Result<String> actual = healthInnerHandler.checkReadiness();
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
    }
    
    @Test
    void checkReadinessFailure() {
        Result<String> actual = healthInnerHandler.checkReadiness();
        assertEquals(ErrorCode.SERVER_ERROR.getCode(), actual.getCode());
    }
}