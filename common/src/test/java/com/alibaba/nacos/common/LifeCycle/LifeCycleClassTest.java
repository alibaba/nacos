package com.alibaba.nacos.common.LifeCycle;

import com.alibaba.nacos.common.lifecycle.AbstractLifeCycle;
import com.alibaba.nacos.common.lifecycle.LifeCycleState;

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
        this.res.start();
        Assert.assertEquals(this.res.getCounter(), 1);
    }

    @After
    public void cleanup() throws Exception{
        this.res.stop();
        Assert.assertEquals(this.res.getCounter(), 0);
        Assert.assertTrue(this.res.isStopped());
    }

    @Test
    public void testResource_LifeCycleMethod() throws Exception{
        this.res.doStart();
        Assert.assertEquals(this.res.getCounter(), 2);
        this.res.doStart();
        Assert.assertEquals(this.res.getCounter(), 3);
        this.res.doStop();
        Assert.assertEquals(this.res.getCounter(), 2);
        this.res.doStop();
        Assert.assertEquals(this.res.getCounter(), 1);
    }

    @Test
    public void testResource_GetState() throws Exception{
        Assert.assertEquals(this.res.getState(), LifeCycleState.STARTED.toString());
        Assert.assertTrue(this.res.isStarted());
        Assert.assertFalse(this.res.isFailed());
        Assert.assertTrue(this.res.isRunning());
        Assert.assertFalse(this.res.isStopped());
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
