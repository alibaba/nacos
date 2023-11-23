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

package com.alibaba.nacos.common.http.param;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HeaderTest {
    
    @Test
    public void testSetContentType() {
        Header header = Header.newInstance();
        header.setContentType(null);
        assertEquals(MediaType.APPLICATION_JSON, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
        header.setContentType(MediaType.MULTIPART_FORM_DATA);
        assertEquals(MediaType.MULTIPART_FORM_DATA, header.getValue(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    public void testHeaderKyeIgnoreCase() {
        Header header = Header.newInstance();
        header.addParam("Content-Encoding", "gzip");
        assertEquals("gzip", header.getValue("content-encoding"));
    }
    
    @Test
    public void testToList() {
        Header header = Header.newInstance();
        List<String> list = header.toList();
        assertTrue(list.contains(HttpHeaderConsts.CONTENT_TYPE));
        assertTrue(list.contains(MediaType.APPLICATION_JSON));
        assertEquals(1, list.indexOf(MediaType.APPLICATION_JSON) - list.indexOf(HttpHeaderConsts.CONTENT_TYPE));
        assertTrue(list.contains(HttpHeaderConsts.ACCEPT_CHARSET));
        assertTrue(list.contains("UTF-8"));
        assertEquals(1, list.indexOf("UTF-8") - list.indexOf(HttpHeaderConsts.ACCEPT_CHARSET));
    }
    
    @Test
    public void testAddAllForMap() {
        Map<String, String> map = new HashMap<>();
        map.put("test1", "test2");
        map.put("test3", "test4");
        Header header = Header.newInstance();
        header.addAll(map);
        assertEquals("test2", header.getValue("test1"));
        assertEquals("test4", header.getValue("test3"));
        assertEquals(4, header.getHeader().size());
    }
    
    @Test
    public void testAddAllForList() {
        List<String> list = new ArrayList<>(4);
        list.add("test1");
        list.add("test2");
        list.add("test3");
        list.add("test4");
        Header header = Header.newInstance();
        header.addAll(list);
        assertEquals("test2", header.getValue("test1"));
        assertEquals("test4", header.getValue("test3"));
        assertEquals(4, header.getHeader().size());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testAddAllForListWithWrongLength() {
        List<String> list = new ArrayList<>(3);
        list.add("test1");
        list.add("test2");
        list.add("test3");
        Header header = Header.newInstance();
        header.addAll(list);
    }
    
    @Test
    public void testAddOriginalResponseHeader() {
        List<String> list = new ArrayList<>(4);
        list.add("test1");
        list.add("test2");
        list.add("test3");
        list.add("test4");
        Header header = Header.newInstance();
        header.addOriginalResponseHeader("test", list);
        assertEquals("test1", header.getValue("test"));
        assertEquals(1, header.getOriginalResponseHeader().size());
        assertEquals(list, header.getOriginalResponseHeader().get("test"));
    }
    
    @Test
    public void testGetCharset() {
        Header header = Header.newInstance();
        assertEquals("UTF-8", header.getCharset());
        header.addParam(HttpHeaderConsts.ACCEPT_CHARSET, null);
        header.setContentType(MediaType.APPLICATION_JSON);
        assertEquals("UTF-8", header.getCharset());
        header.setContentType("application/json;charset=GBK");
        assertEquals("GBK", header.getCharset());
        header.setContentType("application/json");
        assertEquals("UTF-8", header.getCharset());
        header.setContentType("");
        assertEquals("UTF-8", header.getCharset());
    }
    
    @Test
    public void testClear() {
        Header header = Header.newInstance();
        header.addOriginalResponseHeader("test", Collections.singletonList("test"));
        assertEquals(3, header.getHeader().size());
        assertEquals(1, header.getOriginalResponseHeader().size());
        header.clear();
        assertEquals(0, header.getHeader().size());
        assertEquals(0, header.getOriginalResponseHeader().size());
        assertEquals("Header{headerToMap={}}", header.toString());
    }
}
