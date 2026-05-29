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

package com.alibaba.nacos.naming.misc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class NamingExecuteTaskDispatcherTest {
    
    @Test
    void testDestroy() throws Exception {
        Constructor<NamingExecuteTaskDispatcher> constructor =
            NamingExecuteTaskDispatcher.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        NamingExecuteTaskDispatcher dispatcher = constructor.newInstance();
        
        assertNotNull(dispatcher.workersStatus());
        dispatcher.destroy();
    }
}
