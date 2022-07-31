/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package common;

import com.alibaba.nacos.plugin.address.common.AddressProperties;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Date 2022/7/31.
 *
 * @author GuoJiangFu
 */
public class AddressPropertiesTest {
    
    @Test
    public void testSetProperties() {
        String key = "server-addr";
        String serverAddr = "localhost:8080";
        AddressProperties.setProperties(key, serverAddr);
        assertEquals(serverAddr, AddressProperties.getProperty(key));
    }
    
    @Test
    public void testGetProperties() {
        String key = "test-key";
        String val = "test-val";
        assertNull(AddressProperties.getProperty(key));
        AddressProperties.setProperties(key, val);
        assertEquals(val, AddressProperties.getProperty(key));
    }
}
