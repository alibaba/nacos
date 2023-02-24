package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.*;

/**
 * Created by chenwenshun on 2023/2/24.
 */
@RunWith(MockitoJUnitRunner.class)
public class SnowFlakeInstanceIdGeneratorTest {

    @Mock
    MockEnvironment env;


    @Test
    public void testGenerateInstanceId() {
        EnvUtil.setEnvironment(env);
        when(env.getProperty(anyString(), anyObject(), any())).thenReturn(-1);
        SnowFlakeInstanceIdGenerator idGenerator = new SnowFlakeInstanceIdGenerator("hello-service",
                "clusterName", 8080);
        String instanceId = idGenerator.generateInstanceId();
        Assert.assertNotNull(instanceId);
    }

}
