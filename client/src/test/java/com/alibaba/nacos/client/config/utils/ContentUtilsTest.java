/*
 *
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
 *
 */

package com.alibaba.nacos.client.config.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.alibaba.nacos.api.common.Constants.WORD_SEPARATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentUtilsTest {
    
    @Test
    void testVerifyIncrementPubContent() {
        String content = "aabbb";
        ContentUtils.verifyIncrementPubContent(content);
    }
    
    @Test
    void testVerifyIncrementPubContentFail1() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String content = null;
            ContentUtils.verifyIncrementPubContent(content);
        });
        assertTrue(exception.getMessage().contains("publish/delete content can not be null"));
    }
    
    @Test
    void testVerifyIncrementPubContentFail2() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String content = "aa\rbbb";
            ContentUtils.verifyIncrementPubContent(content);
        });
        assertTrue(exception.getMessage().contains("publish/delete content can not contain return and linefeed"));
    }
    
    @Test
    void testVerifyIncrementPubContentFail3() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String content = "";
            ContentUtils.verifyIncrementPubContent(content);
        });
        assertTrue(exception.getMessage().contains("publish/delete content can not be null"));
    }
    
    @Test
    void testVerifyIncrementPubContentFail4() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String content = "aa" + WORD_SEPARATOR + "bbb";
            ContentUtils.verifyIncrementPubContent(content);
        });
        assertTrue(exception.getMessage().contains("publish/delete content can not contain(char)2"));
    }
    
    @Test
    void testGetContentIdentity() {
        String content = "aa" + WORD_SEPARATOR + "bbb";
        String content1 = ContentUtils.getContentIdentity(content);
        assertEquals("aa", content1);
    }
    
    @Test
    void testGetContentIdentityFail() {
        assertThrows(IllegalArgumentException.class, () -> {
            String content = "aabbb";
            ContentUtils.getContentIdentity(content);
        });
    }
    
    @Test
    void testGetContent() {
        String content = "aa" + WORD_SEPARATOR + "bbb";
        String content1 = ContentUtils.getContent(content);
        assertEquals("bbb", content1);
    }
    
    @Test
    void testGetContentFail() {
        assertThrows(IllegalArgumentException.class, () -> {
            String content = "aabbb";
            ContentUtils.getContent(content);
        });
    }
    
    @Test
    void testTruncateContent() {
        String content = "aa";
        String actual = ContentUtils.truncateContent(content);
        assertEquals(content, actual);
    }
    
    @Test
    void testTruncateLongContent() {
        char[] arr = new char[101];
        Arrays.fill(arr, 'a');
        String content = new String(arr);
        String actual = ContentUtils.truncateContent(content);
        assertEquals(content.substring(0, 100) + "...", actual);
    }
    
    @Test
    void testTruncateContentNull() {
        String actual = ContentUtils.truncateContent(null);
        assertEquals("", actual);
    }
}