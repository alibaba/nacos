/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.Test;

/**
 * {@code Preconditions} unit test.
 * @author zzq
 * @date 2021/7/29
 */
public final class PreconditionsTest {
    
    private static final String FORMAT = "I ate %s pies.";
    
    private static final String ARG = "one";
    
    private static final String ERRORMSG = "A message";
    
    @Test
    public void testCheckArgument2Args1true() {
        Preconditions.checkArgument(true, ERRORMSG);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckArgument2Args1false() {
        Preconditions.checkArgument(false, ERRORMSG);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckArgument2Args1true2null() {
        Preconditions.checkArgument(true, null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckArgument2Args1false2null() {
        Preconditions.checkArgument(false, null);
    }
    
    @Test
    public void testCheckArgument3Args1true() {
        Preconditions.checkArgument(true, ERRORMSG, ARG);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckArgument3Args1false() {
        Preconditions.checkArgument(false, ERRORMSG, ARG);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckArgument3Args1true2null() {
        Preconditions.checkArgument(true, null, ARG);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckArgument3Args1false2null() {
        Preconditions.checkArgument(false, null, ARG);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCheckArgument3Args1false3null() {
        Preconditions.checkArgument(false, ERRORMSG, null);
    }
}
