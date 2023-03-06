/*
 *
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
 *
 */

package com.alibaba.nacos.naming.cluster.remote.request;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class DistroDataRequestTest {
    
    @Test
    public void testConstructor1() {
        DistroDataRequest req = new DistroDataRequest();
        assertNotNull(req);
    }
    
    @Test
    public void testConstructor2() {
        DistroDataRequest req = new DistroDataRequest(mock(DistroData.class), mock(DataOperation.class));
        assertNotNull(req);
    }
    
    @Test
    public void testGetterAndSetter() {
        DistroData distroData = mock(DistroData.class);
        DataOperation dataOperation = mock(DataOperation.class);
        DistroDataRequest req = new DistroDataRequest();
        req.setDistroData(distroData);
        req.setDataOperation(dataOperation);
        assertEquals(distroData, req.getDistroData());
        assertEquals(dataOperation, req.getDataOperation());
    }
}