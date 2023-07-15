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

package com.alibaba.nacos.lock.core;

import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.lock.core.connect.LockConnectionManager;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * lock connection manager test.
 *
 * @author 985492783@qq.com
 * @description LockConnectionManagerTest
 * @date 2023/7/13 17:57
 */
@RunWith(MockitoJUnitRunner.class)
public class LockConnectionManagerTest {
    
    private LockConnectionManager lockConnectionManager;
    
    @Mock
    private ProtocolManager protocolManager;
    
    private MockedStatic<ApplicationUtils> mockUtil;
    
    @Before
    public void setUp() {
        mockUtil = Mockito.mockStatic(ApplicationUtils.class);
        mockUtil.when(() -> ApplicationUtils.getBean(ProtocolManager.class)).thenReturn(protocolManager);
        
        lockConnectionManager = new LockConnectionManager();
    }
    
    @After
    public void destroy() {
        mockUtil.close();
    }
    
    @Test
    public void test() {
        String connectId = "connectId";
        lockConnectionManager.createConnectionSync(connectId);
        assertTrue(lockConnectionManager.isAlive(connectId));
        lockConnectionManager.destroyConnectionSync(connectId);
        assertFalse(lockConnectionManager.isAlive(connectId));
    }
}
