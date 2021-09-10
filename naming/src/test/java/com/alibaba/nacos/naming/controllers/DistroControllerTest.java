/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link DistroController} unit test.
 *
 * @author chenglu
 * @date 2021-07-21 19:06
 */
@RunWith(MockitoJUnitRunner.class)
public class DistroControllerTest {
    
    @InjectMocks
    private DistroController distroController;
    
    @Mock
    private DistroProtocol distroProtocol;
    
    @Mock
    private ServiceManager serviceManager;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Test
    public void testOnSyncDatum() {
        Map<String, Datum<Instances>> dataMap = new HashMap<>();
        try {
            distroController.onSyncDatum(dataMap);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof NacosException);
            NacosException exception = (NacosException) e;
            Assert.assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        }
        
        dataMap.put("test", new Datum<>());
        try {
            ResponseEntity responseEntity = distroController.onSyncDatum(dataMap);
            Assert.assertEquals("ok", responseEntity.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testSyncChecksum() {
        Mockito.when(distroProtocol.onVerify(Mockito.any(), Mockito.anyString())).thenReturn(true);
        
        ResponseEntity responseEntity = distroController.syncChecksum("test", new HashMap<>());
        Assert.assertEquals("ok", responseEntity.getBody());
    }
    
    @Test
    public void testGet() {
        try {
            Mockito.when(distroProtocol.onQuery(Mockito.any())).thenReturn(new DistroData(null, "content".getBytes()));
            
            ResponseEntity<byte[]> responseEntity = distroController.get("{\"keys\":[\"12\", \"33\"]}");
            Assert.assertArrayEquals("content".getBytes(), responseEntity.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    @Test
    public void testGetAllDatums() {
        Mockito.when(distroProtocol.onSnapshot(Mockito.any())).thenReturn(new DistroData(null, "content".getBytes()));
        
        ResponseEntity<byte[]> responseEntity = distroController.getAllDatums();
        Assert.assertArrayEquals("content".getBytes(), responseEntity.getBody());
    }
}
