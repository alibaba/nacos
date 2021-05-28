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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;

import static com.alibaba.nacos.api.common.Constants.WORD_SEPARATOR;

public class ContentUtilsTest {
    
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    
    @Test
    public void testVerifyIncrementPubContent() {
        String content = "aabbb";
        ContentUtils.verifyIncrementPubContent(content);
    }
    
    @Test
    public void testVerifyIncrementPubContentFail1() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("发布/删除内容不能为空");
        String content = null;
        ContentUtils.verifyIncrementPubContent(content);
    }
    
    @Test
    public void testVerifyIncrementPubContentFail2() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("发布/删除内容不能包含回车和换行");
        String content = "aa\rbbb";
        ContentUtils.verifyIncrementPubContent(content);
    }
    
    @Test
    public void testVerifyIncrementPubContentFail3() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("发布/删除内容不能为空");
        String content = "";
        ContentUtils.verifyIncrementPubContent(content);
    }
    
    @Test
    public void testVerifyIncrementPubContentFail4() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("发布/删除内容不能包含(char)2");
        String content = "aa" + WORD_SEPARATOR + "bbb";
        ContentUtils.verifyIncrementPubContent(content);
    }
    
    @Test
    public void testGetContentIdentity() {
        String content = "aa" + WORD_SEPARATOR + "bbb";
        String content1 = ContentUtils.getContentIdentity(content);
        Assert.assertEquals("aa", content1);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetContentIdentityFail() {
        String content = "aabbb";
        ContentUtils.getContentIdentity(content);
    }
    
    @Test
    public void testGetContent() {
        String content = "aa" + WORD_SEPARATOR + "bbb";
        String content1 = ContentUtils.getContent(content);
        Assert.assertEquals("bbb", content1);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetContentFail() {
        String content = "aabbb";
        ContentUtils.getContent(content);
    }
    
    @Test
    public void testTruncateContent() {
        String content = "aa";
        String actual = ContentUtils.truncateContent(content);
        Assert.assertEquals(content, actual);
    }
    
    @Test
    public void testTruncateLongContent() {
        char[] arr = new char[101];
        Arrays.fill(arr, 'a');
        String content = new String(arr);
        String actual = ContentUtils.truncateContent(content);
        Assert.assertEquals(content.substring(0, 100) + "...", actual);
    }
    
    @Test
    public void testTruncateContentNull() {
        String actual = ContentUtils.truncateContent(null);
        Assert.assertEquals("", actual);
    }
}