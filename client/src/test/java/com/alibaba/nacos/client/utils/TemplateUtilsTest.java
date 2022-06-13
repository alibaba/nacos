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

package com.alibaba.nacos.client.utils;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TemplateUtilsTest {
    
    @Test
    public void testStringNotEmptyAndThenExecuteSuccess() {
        String word = "run";
        Runnable task = Mockito.mock(Runnable.class);
        TemplateUtils.stringNotEmptyAndThenExecute(word, task);
        Mockito.verify(task, Mockito.times(1)).run();
    }
    
    @Test
    public void testStringNotEmptyAndThenExecuteFail() {
        String word = "";
        Runnable task = Mockito.mock(Runnable.class);
        TemplateUtils.stringNotEmptyAndThenExecute(word, task);
        Mockito.verify(task, Mockito.times(0)).run();
    }
    
    @Test
    public void testStringEmptyAndThenExecuteSuccess() {
        String word = "   ";
        String actual = TemplateUtils.stringEmptyAndThenExecute(word, () -> "call");
        Assert.assertEquals("", actual);
    }
    
    @Test
    public void testStringEmptyAndThenExecuteFail() {
        String word = "";
        final String expect = "call";
        String actual = TemplateUtils.stringEmptyAndThenExecute(word, () -> expect);
        Assert.assertEquals(expect, actual);
    }
    
    @Test
    public void testStringBlankAndThenExecuteSuccess() {
        String word = "success";
        String actual = TemplateUtils.stringBlankAndThenExecute(word, () -> "call");
        Assert.assertEquals(word, actual);
    }
    
    @Test
    public void testStringBlankAndThenExecuteFail() {
        String word = "   ";
        final String expect = "call";
        String actual = TemplateUtils.stringBlankAndThenExecute(word, () -> expect);
        Assert.assertEquals(expect, actual);
    }
}