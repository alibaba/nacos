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
package com.alibaba.nacos.common.LifeCycle;

import com.alibaba.nacos.common.lifecycle.LifeCycle;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test serveral cases for lifecycle class instance.
 *
 * @author zongtanghu
 */
public class LifeCycleClassTest {

    private Resource res;

    @Before
    public void setup() throws Exception{
        this.res = new Resource(0);
        Assert.assertEquals(0, this.res.getCounter());
    }

    @After
    public void cleanup() throws Exception{
        this.res.destroy();
        Assert.assertEquals(0, this.res.getCounter());
    }

    @Test
    public void testResource_LifeCycleMethod() throws Exception{
        this.res.increament();
        Assert.assertEquals(1, this.res.getCounter());
        this.res.increament();
        Assert.assertEquals(2, this.res.getCounter());
        this.res.increament();
        Assert.assertEquals(3, this.res.getCounter());
        this.res.increament();
        Assert.assertEquals(4, this.res.getCounter());
    }

    class Resource implements LifeCycle {

        private int counter;

        public Resource(int counter) {
            this.counter = counter;
        }

        public int getCounter() {
            return counter;
        }

        public void setCounter(int counter) {
            this.counter = counter;
        }

        public void increament() {
            this.counter++;
        }

        @Override
        public void destroy() {
            this.counter = 0;
        }
    }
}
