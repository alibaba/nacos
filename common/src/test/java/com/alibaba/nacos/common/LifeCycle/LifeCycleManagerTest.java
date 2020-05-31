package com.alibaba.nacos.common.LifeCycle;

import com.alibaba.nacos.common.lifecycle.AbstractLifeCycle;
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
