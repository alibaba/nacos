package com.alibaba.nacos.client;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
import com.alibaba.nacos.client.naming.beat.BeatReactor;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author caoyixiong
 */
@RunWith(MockitoJUnitRunner.class)
public class BeatReactorTest {

    @Mock
    private NamingProxy namingProxy;

    @Test
    public void test() throws NoSuchFieldException, IllegalAccessException, InterruptedException, NacosException {
        BeatReactor beatReactor = new BeatReactor(namingProxy);

        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setServiceName("test");
        beatInfo.setIp("11.11.11.11");
        beatInfo.setPort(1234);
        beatInfo.setCluster("clusterName");
        beatInfo.setWeight(1);
        beatInfo.setMetadata(new HashMap<String, String>());
        beatInfo.setScheduled(false);
        beatInfo.setPeriod(1000L);

        Mockito.doReturn(0L).when(namingProxy).sendBeat(beatInfo, true);
        beatReactor.addBeatInfo("testService", beatInfo);

        Assert.assertEquals(1, getActiveThread(beatReactor));
        Thread.sleep(1100L);
        beatReactor.removeBeatInfo("testService", beatInfo.getIp(), beatInfo.getPort());
        Thread.sleep(3100L);
        Assert.assertEquals(0, getActiveThread(beatReactor));
    }

    private int getActiveThread(BeatReactor beatReactor) throws NoSuchFieldException, IllegalAccessException {
        Field field = BeatReactor.class.getDeclaredField("executorService");
        field.setAccessible(true);
        ScheduledThreadPoolExecutor scheduledExecutorService = (ScheduledThreadPoolExecutor) field.get(beatReactor);
        return scheduledExecutorService.getQueue().size();
    }

}
