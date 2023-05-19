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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * MediaTypeTest.
 *
 * @author mai.jh
 */
public class MediaTypeTest {
    
    @Test
    public void testValueOf() {
        MediaType mediaType = MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED);
        String type = "application/x-www-form-urlencoded";
        String charset = "UTF-8";
        assertEquals(type, mediaType.getType());
        assertEquals(charset, mediaType.getCharset());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, mediaType.toString());
    }
    
    @Test
    public void testValueOf2() {
        MediaType mediaType = MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED, "ISO-8859-1");
        String type = "application/x-www-form-urlencoded";
        String charset = "ISO-8859-1";
        String excepted = "application/x-www-form-urlencoded;charset=ISO-8859-1";
        assertEquals(type, mediaType.getType());
        assertEquals(charset, mediaType.getCharset());
        assertEquals(excepted, mediaType.toString());
    }
    
    @Test
    public void testValueOf3() {
        MediaType mediaType = MediaType.valueOf("application/x-www-form-urlencoded", "ISO-8859-1");
        String type = "application/x-www-form-urlencoded";
        String charset = "ISO-8859-1";
        String excepted = "application/x-www-form-urlencoded;charset=ISO-8859-1";
        assertEquals(type, mediaType.getType());
        assertEquals(charset, mediaType.getCharset());
        assertEquals(excepted, mediaType.toString());
    }
    
}
