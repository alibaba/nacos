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

import com.alibaba.nacos.common.lifecycle.AbstractLifeCycle;
import com.alibaba.nacos.common.lifecycle.LifeCycleState;
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
    private static final ResourceLifeCycleManager RESOURCE_MANAGER = ResourceLifeCycleManager.getInstance();

    @Before
    public void setup() throws Exception{
        this.res = new Resource(0);
        this.res.start();
        RESOURCE_MANAGER.register(this.res);
        Assert.assertEquals(this.res.getCounter(), 1);
    }

    @After
    public void cleanup() throws Exception{
        RESOURCE_MANAGER.shutdown();
        Assert.assertEquals(this.res.getCounter(), 0);
        // here, double check shutdown called by two times whether the result is ok.
        RESOURCE_MANAGER.shutdown();
        Assert.assertEquals(this.res.getCounter(), 0);
        // here, check whether the buffer data in resource manager is correct.
        RESOURCE_MANAGER.destroy(this.res);
        Assert.assertEquals(this.res.getCounter(), 0);
    }

    @Test
    public void testLifeCycleManager() throws Exception{
        this.res.doStart();
        Assert.assertEquals(this.res.getCounter(), 2);
        this.res.doStop();
        Assert.assertEquals(this.res.getCounter(), 1);
    }

    @Test
    public void testLifeCycleManager_deregister() throws Exception{

        Resource temp = new Resource(0);
        temp.start();
        RESOURCE_MANAGER.register(temp);
        RESOURCE_MANAGER.deregister(temp);
        RESOURCE_MANAGER.destroy(temp);

        Assert.assertEquals(temp.getCounter(), 1);
    }

    class Resource extends AbstractLifeCycle {

        private int counter;

        public Resource(int counter) {
            super();
            this.counter = counter;
        }


        public int getCounter() {
            return counter;
        }

        public void setCounter(int counter) {
            this.counter = counter;
        }

        @Override
        protected void doStart() throws Exception {
            counter++;
        }

        @Override
        protected void doStop() throws Exception {
            counter--;
        }
    }
}
