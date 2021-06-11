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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpHealthCheckProcessorTest {
    
    @Mock
    private HealthCheckCommonV2 healthCheckCommon;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private HealthCheckTaskV2 healthCheckTaskV2;
    
    @Mock
    private Service service;
    
    @Mock
    private ClusterMetadata clusterMetadata;
    
    @Mock
    private IpPortBasedClient ipPortBasedClient;
    
    @Mock
    private HealthCheckInstancePublishInfo healthCheckInstancePublishInfo;
    
    @Mock
    private RestResult restResult;
    
    @Mock
    private ConnectException connectException;
    
    private HttpHealthCheckProcessor httpHealthCheckProcessor;
    
    @Before
    public void setUp() {
        when(switchDomain.getHttpHealthParams()).thenReturn(new SwitchDomain.HttpHealthParams());
        when(healthCheckTaskV2.getClient()).thenReturn(ipPortBasedClient);
        when(ipPortBasedClient.getInstancePublishInfo(service)).thenReturn(healthCheckInstancePublishInfo);
        httpHealthCheckProcessor = new HttpHealthCheckProcessor(healthCheckCommon, switchDomain);
    }
    
    @Test
    public void testProcess() {
        httpHealthCheckProcessor.process(healthCheckTaskV2, service, clusterMetadata);
        
        verify(healthCheckTaskV2).getClient();
        verify(healthCheckInstancePublishInfo).tryStartCheck();
    }
    
    @Test
    public void testGetType() {
        Assert.assertEquals(httpHealthCheckProcessor.getType(), HealthCheckType.HTTP.name());
    }
    
    @Test
    public void testConstructor()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<HttpHealthCheckProcessor> healthCheckProcessorClass = HttpHealthCheckProcessor.class;
        Class<?>[] classes = healthCheckProcessorClass.getDeclaredClasses();
        Class<?> aClass = Arrays.stream(classes).findFirst().get();
        Constructor<?> constructor = aClass
                .getConstructor(HttpHealthCheckProcessor.class, HealthCheckInstancePublishInfo.class,
                        HealthCheckTaskV2.class, Service.class);
        Object objects = constructor
                .newInstance(httpHealthCheckProcessor, healthCheckInstancePublishInfo, healthCheckTaskV2, service);
        
        Assert.assertNotNull(objects);
    }
    
    @Test
    public void testOnReceive()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<HttpHealthCheckProcessor> healthCheckProcessorClass = HttpHealthCheckProcessor.class;
        Class<?>[] classes = healthCheckProcessorClass.getDeclaredClasses();
        Class<?> aClass = Arrays.stream(classes).findFirst().get();
        Constructor<?> constructor = aClass
                .getConstructor(HttpHealthCheckProcessor.class, HealthCheckInstancePublishInfo.class,
                        HealthCheckTaskV2.class, Service.class);
        Object objects = constructor
                .newInstance(httpHealthCheckProcessor, healthCheckInstancePublishInfo, healthCheckTaskV2, service);
        List<Integer> codeList = Stream
                .of(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_UNAVAILABLE, HttpURLConnection.HTTP_NOT_FOUND)
                .collect(Collectors.toList());
        for (Integer code : codeList) {
            when(restResult.getCode()).thenReturn(code);
            Method onReceive = aClass.getMethod("onReceive", RestResult.class);
            onReceive.invoke(objects, restResult);
            
            //verify
            this.verifyCall(code);
        }
    }
    
    private void verifyCall(int code) {
        switch (code) {
            case HttpURLConnection.HTTP_OK:
                verify(healthCheckCommon).checkOk(healthCheckTaskV2, service, "http:" + restResult.getCode());
                break;
            case HttpURLConnection.HTTP_UNAVAILABLE:
                verify(healthCheckCommon).checkFail(healthCheckTaskV2, service, "http:" + restResult.getCode());
                verify(healthCheckCommon)
                        .reEvaluateCheckRT(healthCheckTaskV2.getCheckRtNormalized() * 2, healthCheckTaskV2,
                                switchDomain.getHttpHealthParams());
                break;
            case HttpURLConnection.HTTP_NOT_FOUND:
                verify(healthCheckCommon).checkFailNow(healthCheckTaskV2, service, "http:" + restResult.getCode());
                verify(healthCheckCommon)
                        .reEvaluateCheckRT(switchDomain.getHttpHealthParams().getMax(), healthCheckTaskV2,
                                switchDomain.getHttpHealthParams());
                break;
            default:
        }
    }
    
    @Test
    public void testOnError()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<HttpHealthCheckProcessor> healthCheckProcessorClass = HttpHealthCheckProcessor.class;
        Class<?>[] classes = healthCheckProcessorClass.getDeclaredClasses();
        Class<?> aClass = Arrays.stream(classes).findFirst().get();
        Constructor<?> constructor = aClass
                .getConstructor(HttpHealthCheckProcessor.class, HealthCheckInstancePublishInfo.class,
                        HealthCheckTaskV2.class, Service.class);
        Object objects = constructor
                .newInstance(httpHealthCheckProcessor, healthCheckInstancePublishInfo, healthCheckTaskV2, service);
        Method onReceive = aClass.getMethod("onError", Throwable.class);
        onReceive.invoke(objects, connectException);
        
        verify(healthCheckCommon)
                .checkFailNow(healthCheckTaskV2, service, "http:unable2connect:" + connectException.getMessage());
        verify(healthCheckCommon).reEvaluateCheckRT(switchDomain.getHttpHealthParams().getMax(), healthCheckTaskV2,
                switchDomain.getHttpHealthParams());
    }
}
