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
import com.alibaba.nacos.common.lifecycle.ResourceLifeCycleManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * Test serveral cases for lifecycle Manager class instance.
 *
 * @author zongtanghu
 */
public class LifeCycleManagerTest {

    private Resource res;
    @Before
    public void setup() throws Exception{
        this.res = new Resource(0);
        ResourceLifeCycleManager.register(this.res);
        Assert.assertEquals(0, this.res.getCounter());
        Assert.assertEquals(1, ResourceLifeCycleManager.getRegisterResourceNum());
    }

    @After
    public void cleanup() throws Exception{
        ResourceLifeCycleManager.shutdown();
        Assert.assertEquals(0, this.res.getCounter());
    }

    @Test
    public void testLifeCycleManager() throws Exception{
        this.res.increament();
        Assert.assertEquals(1, this.res.getCounter());
        this.res.increament();
        Assert.assertEquals(2, this.res.getCounter());
    }

    @Test
    public void testLifeCycleManager_deregister() throws Exception{

        Resource temp = new Resource(0);
        ResourceLifeCycleManager.register(temp);
        temp.increament();
        ResourceLifeCycleManager.deregister(temp);
        ResourceLifeCycleManager.getInstance().destroy(temp);

        Assert.assertEquals(1, temp.getCounter());
        temp.destroy();
        Assert.assertEquals(0, temp.getCounter());
        Assert.assertEquals(0, ResourceLifeCycleManager.getRegisterResourceNum());
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
