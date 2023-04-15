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

package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.distributed.distro.DistroConstants;
import com.alibaba.nacos.naming.controllers.InstanceController;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.ReflectionUtils;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class DistroFilterTest {
    
    @Mock
    private DistroMapper distroMapper;
    
    @Mock
    private ControllerMethodsCache controllerMethodsCache;
    
    @Mock
    private DistroTagGenerator distroTagGenerator;
    
    @Spy
    protected MockEnvironment environment;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @InjectMocks
    private DistroFilter distroFilter;
    
    private ClientAndServer mockServer;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        EnvUtil.setContextPath("/nacos");
        
        System.setProperty(DistroConstants.NACOS_ASYNC_DISTRO_FORWARD_NAME, "true");
        when(environment.getProperty(DistroConstants.NACOS_ASYNC_DISTRO_FORWARD_NAME, Boolean.class,
                DistroConstants.DEFAULT_ASYNC_DISTRO_FORWARD_VALUE)).thenReturn(true);
        EnvUtil.setEnvironment(environment);
        
        ApplicationUtils.injectContext(context);
        when(context.getBean(AuthConfigs.class)).thenReturn(new AuthConfigs());
        
        System.setProperty(DistroConstants.NACOS_ASYNC_DISTRO_FORWARD_NAME, "true");
        mockServer = ClientAndServer.startClientAndServer(8080);
        
        final Method register = ReflectionUtils.findMethod(InstanceController.class, "register",
                HttpServletRequest.class);
        when(controllerMethodsCache.getMethod(any())).thenReturn(register);
        when(distroTagGenerator.getResponsibleTag(any())).thenReturn("tag");
        when(distroMapper.responsible(anyString())).thenReturn(false);
        when(distroMapper.mapSrv(anyString())).thenReturn("127.0.0.1:8080");
    }
    
    @Test
    public void givenAsyncCanForwardRequestToTarget() throws ServletException, IOException, InterruptedException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/nacos/v1/ns/instance");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain filterChain = new MockFilterChain();
        
        new MockServerClient("127.0.0.1", 8080).when(request().withMethod("POST").withPath("/nacos/v1/ns/instance"))
                .respond(response().withStatusCode(200).withBody("ok").withDelay(TimeUnit.SECONDS, 1));
        distroFilter.doFilter(request, response, filterChain);
        final AsyncContext asyncContext = request.getAsyncContext();
        Assert.assertNotNull(asyncContext);
        CountDownLatch latch = new CountDownLatch(1);
        asyncContext.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) throws IOException {
                latch.countDown();
            }
            
            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                latch.countDown();
            }
            
            @Override
            public void onError(AsyncEvent event) throws IOException {
                latch.countDown();
            }
            
            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {
            
            }
        });
        latch.await();
        Assert.assertEquals("ok", response.getContentAsString());
    }
    
    @After
    public void tearDown() {
        mockServer.stop();
    }
}