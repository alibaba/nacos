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
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class GracefulShutdownListenerTest {
    
    @Test
    void testFailedDestroysDispatcher() throws Exception {
        NamingExecuteTaskDispatcher dispatcher = mock(NamingExecuteTaskDispatcher.class);
        try (MockedStatic<NamingExecuteTaskDispatcher> mocked =
            mockStatic(NamingExecuteTaskDispatcher.class)) {
            mocked.when(NamingExecuteTaskDispatcher::getInstance).thenReturn(dispatcher);
            
            new GracefulShutdownListener().failed(null, new RuntimeException("failed"));
            
            verify(dispatcher).destroy();
        }
    }
    
    @Test
    void testFailedIgnoresDestroyException() throws Exception {
        NamingExecuteTaskDispatcher dispatcher = mock(NamingExecuteTaskDispatcher.class);
        doThrow(new Exception("destroy failed")).when(dispatcher).destroy();
        try (MockedStatic<NamingExecuteTaskDispatcher> mocked =
            mockStatic(NamingExecuteTaskDispatcher.class)) {
            mocked.when(NamingExecuteTaskDispatcher::getInstance).thenReturn(dispatcher);
            
            assertDoesNotThrow(
                () -> new GracefulShutdownListener().failed(null, new RuntimeException("failed")));
        }
    }
}
