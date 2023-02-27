package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.anyObject;

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
        Assert.assertTrue(instanceId.endsWith("#8080#clusterName#hello-service"));
    }

}
