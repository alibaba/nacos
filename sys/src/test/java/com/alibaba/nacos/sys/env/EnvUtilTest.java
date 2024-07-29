/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.sys.env;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvUtilTest {

    MockedStatic<OperatingSystemBeanManager> systemBeanManagerMocked;

    @BeforeEach
    void before() {
        systemBeanManagerMocked = Mockito.mockStatic(OperatingSystemBeanManager.class);
    }

    @AfterEach
    void after() {
        if (!systemBeanManagerMocked.isClosed()) {
            systemBeanManagerMocked.close();
        }
    }

    @Test
    public void testGetMem() {
        systemBeanManagerMocked.when(() -> OperatingSystemBeanManager.getFreePhysicalMem()).thenReturn(123L);
        systemBeanManagerMocked.when(() -> OperatingSystemBeanManager.getTotalPhysicalMem()).thenReturn(2048L);
        assertEquals(EnvUtil.getMem(), 1 - ((double) 123L / (double) 2048L));

        systemBeanManagerMocked.when(() -> OperatingSystemBeanManager.getFreePhysicalMem()).thenReturn(0L);
        assertEquals(EnvUtil.getMem(), 1 - ((double) 0L / (double) 2048L));
    }
}
