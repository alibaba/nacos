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

package com.alibaba.nacos.core.util;

import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MapUtilsTest {

    protected Object getVIfMapByRecursive(Object o, int index, String... keys) {
        if (index >= keys.length) {
            return o;
        }
        if (Map.class.isAssignableFrom(o.getClass())) {
            return getVIfMapByRecursive(((Map) o).get(keys[index]), index + 1, keys);
        }
        return null;
    }


    @Test
    public void test_getVIfMapByRecursive() {

        final String key = "num";

        Map<String, Object> map = new HashMap<>();

        // layer 1

        Map<String, Object> subMap_1 = new HashMap<>();
        subMap_1.put(key, 1);
        map.put(key, subMap_1);

        // layer 2

        Map<String, Object> subMap_2 = new HashMap<>();
        subMap_2.put(key, 1);
        subMap_1.put(key, subMap_2);

        System.out.println(map);

        Assert.assertEquals(1, getVIfMapByRecursive(map, 0, key, key, key));

    }

}
