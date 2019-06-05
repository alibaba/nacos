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

package com.alibaba.nacos.client;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

/**
 * @author liaochuntao
 * @date 2019-06-05 20:55
 **/
public class RandomTest {

    private Random random;

    @Before
    public void before() {
        random = new Random();
    }

    @Test
    public void test() throws InterruptedException {
        long[] tmp = new long[10];
        for (int i = 0; i < 10; i++) {
            tmp[i] = random.nextInt(Integer.MAX_VALUE);
        }
        Thread.sleep(100);
        for (int i = 0; i < 10; i++) {
            System.out.println(tmp[i] == random.nextInt(Integer.MAX_VALUE));
        }
    }

}
