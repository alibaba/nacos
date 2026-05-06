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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.constant.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class ContentUtilsTest {
    
    @Test
    void testVerifyIncrementPubContent() {
        
        String content = "";
        try {
            ContentUtils.verifyIncrementPubContent(content);
            fail();
        } catch (IllegalArgumentException e) {
            assertNotNull(e.toString());
        }
        
        content = "\r";
        try {
            ContentUtils.verifyIncrementPubContent(content);
            fail();
        } catch (IllegalArgumentException e) {
            assertNotNull(e.toString());
        }
        
        content = "\n";
        try {
            ContentUtils.verifyIncrementPubContent(content);
            fail();
        } catch (IllegalArgumentException e) {
            assertNotNull(e.toString());
        }
        
        content = Constants.WORD_SEPARATOR + "test";
        try {
            ContentUtils.verifyIncrementPubContent(content);
            fail();
        } catch (IllegalArgumentException e) {
            assertNotNull(e.toString());
        }
        
    }
    
    @Test
    void testGetContentIdentity() {
        String content = "abc" + Constants.WORD_SEPARATOR + "edf";
        String result = ContentUtils.getContentIdentity(content);
        assertEquals("abc", result);
        
        content = "test";
        try {
            ContentUtils.getContentIdentity(content);
            fail();
        } catch (IllegalArgumentException e) {
            assertNotNull(e.toString());
        }
        
    }
    
    @Test
    void testGetContent() {
        String content = "abc" + Constants.WORD_SEPARATOR + "edf";
        String result = ContentUtils.getContent(content);
        assertEquals("edf", result);
        
        content = "test";
        try {
            ContentUtils.getContent(content);
            fail();
        } catch (IllegalArgumentException e) {
            assertNotNull(e.toString());
        }
        
    }
    
    @Test
    void testTruncateContent() {
        String content = "test";
        String result = ContentUtils.truncateContent(content);
        assertEquals(content, result);
        
        String content2 = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
        String result2 = ContentUtils.truncateContent(content2);
        String expected = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuv...";
        assertEquals(expected, result2);
        
        assertEquals("", ContentUtils.truncateContent(null));
    }
}
