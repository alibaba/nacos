/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.cluster.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleHealthCheckerHolderTest {
    
    @Test
    void testGetInstance() {
        ModuleHealthCheckerHolder instance1 = ModuleHealthCheckerHolder.getInstance();
        ModuleHealthCheckerHolder instance2 = ModuleHealthCheckerHolder.getInstance();
        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }
    
    @Test
    void testCheckReadinessWithPassingStubChecker() {
        AbstractModuleHealthChecker passingChecker = new AbstractModuleHealthChecker() {
            
            @Override
            public boolean readiness() {
                return true;
            }
            
            @Override
            public String getModuleName() {
                return "PassingStubModule";
            }
        };
        ModuleHealthCheckerHolder.getInstance().registerChecker(passingChecker);
        ReadinessResult result = ModuleHealthCheckerHolder.getInstance().checkReadiness();
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("OK", result.getResultMessage());
    }
    
    @Test
    void testCheckReadinessWithFailingStubChecker() {
        AbstractModuleHealthChecker failingChecker = new AbstractModuleHealthChecker() {
            
            @Override
            public boolean readiness() {
                return false;
            }
            
            @Override
            public String getModuleName() {
                return "FailingStubModuleForTest";
            }
        };
        ModuleHealthCheckerHolder.getInstance().registerChecker(failingChecker);
        ReadinessResult result = ModuleHealthCheckerHolder.getInstance().checkReadiness();
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getResultMessage().contains("FailingStubModuleForTest"));
        assertTrue(result.getResultMessage().contains("not in readiness"));
    }
}
