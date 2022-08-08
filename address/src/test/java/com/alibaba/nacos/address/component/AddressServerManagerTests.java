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

package com.alibaba.nacos.address.component;

import com.alibaba.nacos.address.constant.AddressServerConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AddressServerManagerTests {
    
    private static final AddressServerManager ADDRESS_SERVER_MANAGER = new AddressServerManager();
    
    @Test
    public void getRawProductName() {
        assertEquals(AddressServerConstants.DEFAULT_PRODUCT, ADDRESS_SERVER_MANAGER.getRawProductName(""));
        assertEquals(AddressServerConstants.DEFAULT_PRODUCT,
                ADDRESS_SERVER_MANAGER.getRawProductName(AddressServerConstants.DEFAULT_PRODUCT));
        assertEquals("otherProduct", ADDRESS_SERVER_MANAGER.getRawProductName("otherProduct"));
    }
    
    @Test
    public void getDefaultClusterNameIfEmpty() {
        assertEquals(AddressServerConstants.DEFAULT_GET_CLUSTER, ADDRESS_SERVER_MANAGER.getDefaultClusterNameIfEmpty(""));
        assertEquals(AddressServerConstants.DEFAULT_GET_CLUSTER,
                ADDRESS_SERVER_MANAGER.getDefaultClusterNameIfEmpty(AddressServerConstants.DEFAULT_GET_CLUSTER));
        assertEquals("otherServerList", ADDRESS_SERVER_MANAGER.getDefaultClusterNameIfEmpty("otherServerList"));
    }
    
    @Test
    public void testGetRawClusterName() {
        assertEquals("serverList", ADDRESS_SERVER_MANAGER.getRawClusterName("serverList"));
        assertEquals(AddressServerConstants.DEFAULT_GET_CLUSTER, ADDRESS_SERVER_MANAGER.getRawClusterName(""));
    }
    
    @Test
    public void testSplitIps() {
        final String[] emptyArr = ADDRESS_SERVER_MANAGER.splitIps("");
        assertEquals(0, emptyArr.length);
        final String[] one = ADDRESS_SERVER_MANAGER.splitIps("192.168.1.12:8848");
        assertEquals(1, one.length);
        assertEquals("192.168.1.12:8848", one[0]);
        final String[] two = ADDRESS_SERVER_MANAGER.splitIps("192.168.1.12:8848,192.268.3.33:8848");
        assertEquals(2, two.length);
        assertEquals("192.168.1.12:8848", two[0]);
        assertEquals("192.268.3.33:8848", two[1]);
    }
    
}
