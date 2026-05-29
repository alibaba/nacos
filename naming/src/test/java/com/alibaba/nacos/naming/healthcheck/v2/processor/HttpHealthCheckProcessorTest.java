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
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpHealthCheckProcessorTest {
    
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
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        when(switchDomain.getHttpHealthParams()).thenReturn(new SwitchDomain.HttpHealthParams());
        when(healthCheckTaskV2.getClient()).thenReturn(ipPortBasedClient);
        when(ipPortBasedClient.getInstancePublishInfo(service))
            .thenReturn(healthCheckInstancePublishInfo);
        httpHealthCheckProcessor = new HttpHealthCheckProcessor(healthCheckCommon, switchDomain);
    }
    
    @Test
    void testProcess() {
        httpHealthCheckProcessor.process(healthCheckTaskV2, service, clusterMetadata);
        
        verify(healthCheckTaskV2).getClient();
        verify(healthCheckInstancePublishInfo).tryStartCheck();
    }
    
    @Test
    void testProcessReturnsWhenInstanceMissing() {
        when(ipPortBasedClient.getInstancePublishInfo(service)).thenReturn(null);
        
        httpHealthCheckProcessor.process(healthCheckTaskV2, service, clusterMetadata);
        
        verify(healthCheckTaskV2).getClient();
    }
    
    @Test
    void testProcessReevaluatesWhenPreviousCheckStillRunning() {
        when(healthCheckInstancePublishInfo.tryStartCheck()).thenReturn(false);
        when(healthCheckTaskV2.getCheckRtNormalized()).thenReturn(10L);
        
        httpHealthCheckProcessor.process(healthCheckTaskV2, service, clusterMetadata);
        
        verify(healthCheckCommon).reEvaluateCheckRt(20L, healthCheckTaskV2,
            switchDomain.getHttpHealthParams());
    }
    
    @Test
    void testProcessHandlesHealthCheckerFailure() {
        when(healthCheckInstancePublishInfo.tryStartCheck()).thenReturn(true);
        
        httpHealthCheckProcessor.process(healthCheckTaskV2, service, clusterMetadata);
        
        verify(healthCheckInstancePublishInfo)
            .setCheckRt(switchDomain.getHttpHealthParams().getMax());
        verify(healthCheckCommon).checkFail(eq(healthCheckTaskV2), eq(service),
            startsWith("http:error:"));
        verify(healthCheckCommon).reEvaluateCheckRt(switchDomain.getHttpHealthParams().getMax(),
            healthCheckTaskV2, switchDomain.getHttpHealthParams());
    }
    
    @Test
    void testGetType() {
        assertEquals(httpHealthCheckProcessor.getType(), HealthCheckType.HTTP.name());
    }
    
    @Test
    void testConstructor() throws NoSuchMethodException, IllegalAccessException,
        InvocationTargetException, InstantiationException {
        Class<HttpHealthCheckProcessor> healthCheckProcessorClass = HttpHealthCheckProcessor.class;
        Class<?>[] classes = healthCheckProcessorClass.getDeclaredClasses();
        Class<?> aClass = Arrays.stream(classes).findFirst().get();
        Constructor<?> constructor = aClass.getConstructor(HttpHealthCheckProcessor.class,
            HealthCheckInstancePublishInfo.class,
            HealthCheckTaskV2.class, Service.class);
        Object objects = constructor.newInstance(httpHealthCheckProcessor,
            healthCheckInstancePublishInfo, healthCheckTaskV2, service);
        
        assertNotNull(objects);
    }
    
    @Test
    void testOnReceiveWithOK()
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
        InstantiationException, InterruptedException {
        Class<HttpHealthCheckProcessor> healthCheckProcessorClass = HttpHealthCheckProcessor.class;
        Class<?>[] classes = healthCheckProcessorClass.getDeclaredClasses();
        Class<?> aClass = Arrays.stream(classes).findFirst().get();
        Constructor<?> constructor = aClass.getConstructor(HttpHealthCheckProcessor.class,
            HealthCheckInstancePublishInfo.class,
            HealthCheckTaskV2.class, Service.class);
        Object objects = constructor.newInstance(httpHealthCheckProcessor,
            healthCheckInstancePublishInfo, healthCheckTaskV2, service);
        int code = HttpURLConnection.HTTP_OK;
        when(restResult.getCode()).thenReturn(code);
        Method onReceive = aClass.getMethod("onReceive", RestResult.class);
        onReceive.invoke(objects, restResult);
        //verify
        this.verifyCall(code);
        List<Integer> codeList =
            Stream.of(HttpURLConnection.HTTP_UNAVAILABLE, HttpURLConnection.HTTP_NOT_FOUND)
                .collect(Collectors.toList());
    }
    
    @Test
    void testOnReceiveWithUnavailable()
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
        InstantiationException, InterruptedException {
        Class<HttpHealthCheckProcessor> healthCheckProcessorClass = HttpHealthCheckProcessor.class;
        Class<?>[] classes = healthCheckProcessorClass.getDeclaredClasses();
        Class<?> aClass = Arrays.stream(classes).findFirst().get();
        Constructor<?> constructor = aClass.getConstructor(HttpHealthCheckProcessor.class,
            HealthCheckInstancePublishInfo.class,
            HealthCheckTaskV2.class, Service.class);
        Object objects = constructor.newInstance(httpHealthCheckProcessor,
            healthCheckInstancePublishInfo, healthCheckTaskV2, service);
        int code = HttpURLConnection.HTTP_UNAVAILABLE;
        when(restResult.getCode()).thenReturn(code);
        Method onReceive = aClass.getMethod("onReceive", RestResult.class);
        onReceive.invoke(objects, restResult);
        //verify
        this.verifyCall(code);
    }
    
    @Test
    void testOnReceiveWithMovedTemp()
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
        InstantiationException {
        Object objects = newCallback();
        int code = HttpURLConnection.HTTP_MOVED_TEMP;
        when(restResult.getCode()).thenReturn(code);
        Method onReceive = objects.getClass().getMethod("onReceive", RestResult.class);
        onReceive.invoke(objects, restResult);
        
        verify(healthCheckCommon).checkFail(healthCheckTaskV2, service,
            "http:" + restResult.getCode());
    }
    
    @Test
    void testOnReceiveWithNotFound()
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
        InstantiationException, InterruptedException {
        Class<HttpHealthCheckProcessor> healthCheckProcessorClass = HttpHealthCheckProcessor.class;
        Class<?>[] classes = healthCheckProcessorClass.getDeclaredClasses();
        Class<?> aClass = Arrays.stream(classes).findFirst().get();
        Constructor<?> constructor = aClass.getConstructor(HttpHealthCheckProcessor.class,
            HealthCheckInstancePublishInfo.class,
            HealthCheckTaskV2.class, Service.class);
        Object objects = constructor.newInstance(httpHealthCheckProcessor,
            healthCheckInstancePublishInfo, healthCheckTaskV2, service);
        int code = HttpURLConnection.HTTP_NOT_FOUND;
        when(restResult.getCode()).thenReturn(code);
        Method onReceive = aClass.getMethod("onReceive", RestResult.class);
        onReceive.invoke(objects, restResult);
        //verify
        this.verifyCall(code);
    }
    
    private void verifyCall(int code) {
        switch (code) {
            case HttpURLConnection.HTTP_OK:
                verify(healthCheckCommon).checkOk(healthCheckTaskV2, service,
                    "http:" + restResult.getCode());
                break;
            case HttpURLConnection.HTTP_UNAVAILABLE:
                verify(healthCheckCommon).checkFail(healthCheckTaskV2, service,
                    "http:" + restResult.getCode());
                verify(healthCheckCommon).reEvaluateCheckRt(
                    healthCheckTaskV2.getCheckRtNormalized() * 2, healthCheckTaskV2,
                    switchDomain.getHttpHealthParams());
                break;
            case HttpURLConnection.HTTP_NOT_FOUND:
                verify(healthCheckCommon).checkFailNow(healthCheckTaskV2, service,
                    "http:" + restResult.getCode());
                verify(healthCheckCommon).reEvaluateCheckRt(
                    switchDomain.getHttpHealthParams().getMax(), healthCheckTaskV2,
                    switchDomain.getHttpHealthParams());
                break;
            default:
        }
    }
    
    @Test
    void testOnError() throws NoSuchMethodException, IllegalAccessException,
        InvocationTargetException, InstantiationException {
        Object objects = newCallback();
        Method onReceive = objects.getClass().getMethod("onError", Throwable.class);
        onReceive.invoke(objects, connectException);
        
        verify(healthCheckCommon).checkFailNow(healthCheckTaskV2, service,
            "http:unable2connect:" + connectException.getMessage());
        verify(healthCheckCommon).reEvaluateCheckRt(switchDomain.getHttpHealthParams().getMax(),
            healthCheckTaskV2,
            switchDomain.getHttpHealthParams());
    }
    
    @Test
    void testOnErrorWithTimeoutException()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
        InstantiationException {
        Object objects = newCallback();
        when(healthCheckTaskV2.getCheckRtNormalized()).thenReturn(10L);
        Method onReceive = objects.getClass().getMethod("onError", Throwable.class);
        onReceive.invoke(objects, new SocketTimeoutException("timeout"));
        
        verify(healthCheckCommon).checkFail(healthCheckTaskV2, service, "http:timeout");
        verify(healthCheckCommon).reEvaluateCheckRt(20L, healthCheckTaskV2,
            switchDomain.getHttpHealthParams());
    }
    
    @Test
    void testOnErrorWithGenericException()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
        InstantiationException {
        Object objects = newCallback();
        Method onReceive = objects.getClass().getMethod("onError", Throwable.class);
        onReceive.invoke(objects, new IllegalStateException("broken"));
        
        verify(healthCheckCommon).checkFail(healthCheckTaskV2, service, "http:error:broken");
        verify(healthCheckCommon).reEvaluateCheckRt(switchDomain.getHttpHealthParams().getMax(),
            healthCheckTaskV2, switchDomain.getHttpHealthParams());
    }
    
    @Test
    void testOnCancel()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException,
        InstantiationException {
        Object objects = newCallback();
        Method onCancel = objects.getClass().getMethod("onCancel");
        
        onCancel.invoke(objects);
    }
    
    private Object newCallback() throws NoSuchMethodException, InvocationTargetException,
        InstantiationException, IllegalAccessException {
        Class<HttpHealthCheckProcessor> healthCheckProcessorClass = HttpHealthCheckProcessor.class;
        Class<?>[] classes = healthCheckProcessorClass.getDeclaredClasses();
        Class<?> aClass = Arrays.stream(classes).findFirst().get();
        Constructor<?> constructor = aClass.getConstructor(HttpHealthCheckProcessor.class,
            HealthCheckInstancePublishInfo.class,
            HealthCheckTaskV2.class, Service.class);
        return constructor.newInstance(httpHealthCheckProcessor, healthCheckInstancePublishInfo,
            healthCheckTaskV2, service);
    }
}
