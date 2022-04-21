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

package com.alibaba.nacos.naming.core.v2.pojo;

import org.apache.commons.collections.CollectionUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * test CollectionUtils.isEqualCollection
 *
 * @author <a href="mailto:chenhao26@xiaomi.com">chenhao26</a>
 */
public class CollectionUtilsTest {
    
    @Test
    public void testIsEqualCollection() {
        BatchInstancePublishInfo b1 = new BatchInstancePublishInfo();
        b1.setPort(8907);
        Map<String, Object> map = new HashMap<>();
        map.put("k1", "v1");
        b1.setExtendDatum(map);
        b1.setHealthy(true);
        b1.setIp("1.1.1.2");
        
        BatchInstancePublishInfo b2 = new BatchInstancePublishInfo();
        Map<String, Object> map2 = new HashMap<>();
        map2.put("k1", "v1");
        b2.setPort(8907);
        b2.setExtendDatum(map2);
        b2.setHealthy(true);
        b2.setIp("1.1.1.2");
        
        List<BatchInstancePublishInfo> list1 = new ArrayList<>();
        list1.add(b1);
        List<BatchInstancePublishInfo> list2 = new ArrayList<>();
        list2.add(b2);
        
        boolean collection = CollectionUtils.isEqualCollection(list1, list2);
        System.out.println("collection: " + collection);
    }
    
}
