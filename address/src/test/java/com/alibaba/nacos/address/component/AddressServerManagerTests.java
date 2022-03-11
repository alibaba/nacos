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
    
}
