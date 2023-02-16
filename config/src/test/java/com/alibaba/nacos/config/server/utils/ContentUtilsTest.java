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
import org.junit.Assert;
import org.junit.Test;

public class ContentUtilsTest {
    
    @Test
    public void testVerifyIncrementPubContent() {
        
        String content = "";
        try {
            ContentUtils.verifyIncrementPubContent(content);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertNotNull(e.toString());
        }
        
        content = "\r";
        try {
            ContentUtils.verifyIncrementPubContent(content);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertNotNull(e.toString());
        }
        
        content = "\n";
        try {
            ContentUtils.verifyIncrementPubContent(content);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertNotNull(e.toString());
        }
        
        content = Constants.WORD_SEPARATOR + "test";
        try {
            ContentUtils.verifyIncrementPubContent(content);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertNotNull(e.toString());
        }
        
    }
    
    @Test
    public void testGetContentIdentity() {
        String content = "abc" + Constants.WORD_SEPARATOR + "edf";
        String result = ContentUtils.getContentIdentity(content);
        Assert.assertEquals("abc", result);
        
        content = "test";
        try {
            ContentUtils.getContentIdentity(content);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertNotNull(e.toString());
        }
        
    }
    
    @Test
    public void testGetContent() {
        String content = "abc" + Constants.WORD_SEPARATOR + "edf";
        String result = ContentUtils.getContent(content);
        Assert.assertEquals("edf", result);
        
        content = "test";
        try {
            ContentUtils.getContent(content);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertNotNull(e.toString());
        }
        
    }
    
    @Test
    public void testTruncateContent() {
        String content = "test";
        String result = ContentUtils.truncateContent(content);
        Assert.assertEquals(content, result);
        
        String content2 = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
        String result2 = ContentUtils.truncateContent(content2);
        String expected = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuv...";
        Assert.assertEquals(expected, result2);
        
        Assert.assertEquals("", ContentUtils.truncateContent(null));
    }
}
