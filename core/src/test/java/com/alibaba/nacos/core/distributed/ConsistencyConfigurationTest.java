/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed;

import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * {@link ConsistencyConfiguration} unit test.
 */
@ExtendWith(MockitoExtension.class)
class ConsistencyConfigurationTest {
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Test
    void testStrongAgreementProtocolReturnsProtocol() throws Exception {
        ConsistencyConfiguration config = new ConsistencyConfiguration();
        CPProtocol protocol = config.strongAgreementProtocol(memberManager);
        assertNotNull(protocol);
    }
    
    @Test
    void testGetProtocolUsesBuilderWhenIteratorEmpty() throws Exception {
        ConsistencyConfiguration config = new ConsistencyConfiguration();
        Method getProtocol = ConsistencyConfiguration.class.getDeclaredMethod("getProtocol",
            Class.class, Callable.class);
        getProtocol.setAccessible(true);
        CPProtocol mockProtocol = mock(CPProtocol.class);
        Callable<CPProtocol> builder = () -> mockProtocol;
        Object result = getProtocol.invoke(config, CPProtocol.class, builder);
        assertSame(mockProtocol, result);
    }
}
