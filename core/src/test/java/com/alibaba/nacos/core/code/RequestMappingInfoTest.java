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

package com.alibaba.nacos.core.code;

import com.alibaba.nacos.core.code.condition.ParamRequestCondition;
import com.alibaba.nacos.core.code.condition.PathRequestCondition;

import static com.alibaba.nacos.sys.env.Constants.REQUEST_PATH_SEPARATOR;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestMappingInfoTest {
    
    @Test
    void testSetAndGetParamRequestCondition() {
        RequestMappingInfo info = new RequestMappingInfo();
        ParamRequestCondition paramCondition = new ParamRequestCondition("key=value");
        info.setParamRequestCondition(paramCondition);
        assertEquals(paramCondition, info.getParamRequestCondition());
    }
    
    @Test
    void testSetPathRequestCondition() {
        RequestMappingInfo info = new RequestMappingInfo();
        PathRequestCondition pathCondition =
            new PathRequestCondition("GET" + REQUEST_PATH_SEPARATOR + "/nacos/v1/ns/instance");
        info.setPathRequestCondition(pathCondition);
        assertNotNull(info.toString());
        assertTrue(info.toString().contains("pathRequestCondition"));
    }
    
    @Test
    void testToString() {
        RequestMappingInfo info = new RequestMappingInfo();
        info.setParamRequestCondition(new ParamRequestCondition("a=1"));
        String s = info.toString();
        assertNotNull(s);
        assertTrue(s.contains("RequestMappingInfo"));
        assertTrue(s.contains("paramRequestCondition"));
    }
    
    @Test
    void testRequestMappingInfoComparatorOrdersByParamExpressionSizeDescending() {
        RequestMappingInfo infoFew = new RequestMappingInfo();
        infoFew.setParamRequestCondition(new ParamRequestCondition("a=1"));
        RequestMappingInfo infoMore = new RequestMappingInfo();
        infoMore.setParamRequestCondition(new ParamRequestCondition("a=1", "b=2", "c=3"));
        
        RequestMappingInfo.RequestMappingInfoComparator comparator =
            new RequestMappingInfo.RequestMappingInfoComparator();
        assertTrue(comparator.compare(infoMore, infoFew) < 0);
        assertTrue(comparator.compare(infoFew, infoMore) > 0);
        assertEquals(0, comparator.compare(infoFew, infoFew));
    }
    
    @Test
    void testRequestMappingInfoComparatorSortList() {
        RequestMappingInfo info0 = new RequestMappingInfo();
        info0.setParamRequestCondition(new ParamRequestCondition());
        RequestMappingInfo info2 = new RequestMappingInfo();
        info2.setParamRequestCondition(new ParamRequestCondition("a=1", "b=2"));
        RequestMappingInfo info1 = new RequestMappingInfo();
        info1.setParamRequestCondition(new ParamRequestCondition("x=1"));
        
        List<RequestMappingInfo> list = Arrays.asList(info0, info2, info1);
        list.sort(new RequestMappingInfo.RequestMappingInfoComparator());
        assertEquals(2, list.get(0).getParamRequestCondition().getExpressions().size());
        assertEquals(1, list.get(1).getParamRequestCondition().getExpressions().size());
        assertEquals(0, list.get(2).getParamRequestCondition().getExpressions().size());
    }
}
