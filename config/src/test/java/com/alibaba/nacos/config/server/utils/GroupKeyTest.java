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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class GroupKeyTest {

    @Test
    public void test_parseGroupKey_非法的() {
        String key = "11111+222+333333+444";
        try {
            GroupKey2.parseKey(key);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }

        key = "11111+";
        try {
            GroupKey2.parseKey(key);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }

        key = "11111%29+222";
        try {
            GroupKey2.parseKey(key);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }

        key = "11111%2b+222";
        try {
            GroupKey2.parseKey(key);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }

        key = "11111%25+222";
        String[] pair = GroupKey2.parseKey(key);
        Assert.assertEquals("11111%", pair[0]);
        Assert.assertEquals("222", pair[1]);
    }
}
