/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ListenerCheckResult;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.service.notify.HttpClientManager;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringJUnit4ClassRunner.class)
public class ConfigSubServiceTest {
    
    private ConfigSubService configSubService;
    
    @Mock
    ServerMemberManager serverMemberManager;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<HttpClientManager> httpClientManagerMockedStatic;
    
    @Mock
    NacosRestTemplate nacosRestTemplate;
    
    @Mock
    NacosAsyncRestTemplate nacosAsyncRestTemplate;
    
    @Before
    public void startUP() {
        
        httpClientManagerMockedStatic = Mockito.mockStatic(HttpClientManager.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        configSubService = new ConfigSubService(serverMemberManager);
        envUtilMockedStatic.when(() -> EnvUtil.getContextPath()).thenReturn("/nacos");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), anyString())).thenReturn("mock string");
        EnvUtil.setContextPath("/nacos");
        httpClientManagerMockedStatic.when(() -> HttpClientManager.getNacosRestTemplate())
                .thenReturn(nacosRestTemplate);
        httpClientManagerMockedStatic.when(() -> HttpClientManager.getNacosAsyncRestTemplate())
                .thenReturn(nacosAsyncRestTemplate);
    }
    
    @After
    public void after() {
        if (!envUtilMockedStatic.isClosed()) {
            envUtilMockedStatic.close();
        }
        httpClientManagerMockedStatic.close();
    }
    
    @Test
    public void testGetCollectSampleResult() throws Exception {
        envUtilMockedStatic.close();
        EnvUtil.setContextPath("/nacos");
        ConfigurableEnvironment environment = Mockito.mock(ConfigurableEnvironment.class);
        EnvUtil.setEnvironment(environment);
        Mockito.when(environment.getProperty(anyString(), anyString())).thenReturn("/nacos");
        
        Map<String, Member> mockedMembers = new HashMap<>();
        mockedMembers.put("127.0.0.1", createMember("127.0.0.1"));
        mockedMembers.put("127.0.0.2", createMember("127.0.0.2"));
        mockedMembers.put("127.0.0.3", createMember("127.0.0.3"));
        //mock server member
        Mockito.when(serverMemberManager.allMembers()).thenReturn(mockedMembers.values());
        Mockito.when(serverMemberManager.getServerList()).thenReturn(mockedMembers);
        String dataId = "dataid1234";
        String group = "group34567";
        String tenant = "tenant456789";
        int sampleTimes = 3;
        //cant mock static method cross thread,so cant verify return obj here.
        configSubService.getCollectSampleResult(dataId, group, tenant, sampleTimes);
        configSubService.getCollectSampleResultByIp("127.0.0.1", 3);
        configSubService.getCheckHasListenerResult(dataId, group, tenant, 3);
    }
    
    @Test
    public void testRunSingleJob() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("dataId", "d1");
        params.put("group", "g1");
        params.put("tenant", "t1");
        
        HttpRestResult<Object> httpRestResult = new HttpRestResult<>();
        httpRestResult.setCode(200);
        httpRestResult.setMessage("success");
        
        SampleResult sampleResult1 = new SampleResult();
        Map<String, String> listener1 = new HashMap<>();
        listener1.put("config1", "md51123");
        listener1.put("config11", "md5123123");
        sampleResult1.setLisentersGroupkeyStatus(listener1);
        String mockJsonString = JacksonUtils.toJson(sampleResult1);
        httpRestResult.setData(mockJsonString);
        //mock success
        Mockito.when(nacosRestTemplate.get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class)))
                .thenReturn(httpRestResult);
        String url = "url";
        SampleResult returnObj = (SampleResult) ConfigSubService.runSingleJob("127.0.0.1", params, url,
                SampleResult.class);
        Assert.assertEquals(sampleResult1.getLisentersGroupkeyStatus(), returnObj.getLisentersGroupkeyStatus());
        //mock fail response
        httpRestResult.setCode(500);
        Mockito.when(nacosRestTemplate.get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class)))
                .thenReturn(httpRestResult);
        SampleResult returnObj500 = (SampleResult) ConfigSubService.runSingleJob("127.0.0.1", params, url,
                SampleResult.class);
        Assert.assertEquals(null, returnObj500);
        
        //mock get url throw exception
        Mockito.when(nacosRestTemplate.get(anyString(), any(Header.class), eq(Query.EMPTY), eq(String.class)))
                .thenThrow(new NacosRuntimeException(500, "timeout"));
        SampleResult returnObjTimeout = (SampleResult) ConfigSubService.runSingleJob("127.0.0.1", params, url,
                SampleResult.class);
        Assert.assertEquals(null, returnObjTimeout);
        
    }
    
    @Test
    public void testClusterListenerJob() throws Exception {
        Map<String, Member> mockedMembers = new HashMap<>();
        mockedMembers.put("127.0.0.1", createMember("127.0.0.1"));
        mockedMembers.put("127.0.0.2", createMember("127.0.0.2"));
        mockedMembers.put("127.0.0.3", createMember("127.0.0.3"));
        //mock server member
        Mockito.when(serverMemberManager.allMembers()).thenReturn(mockedMembers.values());
        Mockito.when(serverMemberManager.getServerList()).thenReturn(mockedMembers);
        
        CompletionService mockService = Mockito.mock(CompletionService.class);
        //mock all success
        Mockito.when(mockService.poll(anyLong(), any(TimeUnit.class)))
                .thenReturn(createSampleResultFuture(true, true), createSampleResultFuture(true, true),
                        createSampleResultFuture(true, true));
        Map<String, String> params = new HashMap<>();
        ConfigSubService.ClusterListenerJob clusterListenerJob = new ConfigSubService.ClusterListenerJob(params,
                mockService, serverMemberManager);
        List<SampleResult> sampleResults = clusterListenerJob.runJobs();
        Assert.assertEquals(3, sampleResults.size());
        
        //mock success with exception
        Mockito.when(mockService.poll(anyLong(), any(TimeUnit.class)))
                .thenReturn(createSampleResultFuture(true, true), createSampleResultFuture(false, false))
                .thenThrow(new NacosRuntimeException(500, "13"));
        Map<String, String> params2 = new HashMap<>();
        ConfigSubService.ClusterListenerJob clusterListenerJob2 = new ConfigSubService.ClusterListenerJob(params2,
                mockService, serverMemberManager);
        List<SampleResult> sampleResults2 = clusterListenerJob2.runJobs();
        Assert.assertEquals(1, sampleResults2.size());
        Assert.assertEquals(false, sampleResults2.get(0).getLisentersGroupkeyStatus().isEmpty());
        
    }
    
    @Test
    public void testMergeSampleResult() throws Exception {
        SampleResult sampleResult1 = new SampleResult();
        Map<String, String> listener1 = new HashMap<>();
        listener1.put("config1", "md51123");
        listener1.put("config11", "md5123123");
        sampleResult1.setLisentersGroupkeyStatus(listener1);
        SampleResult sampleResult2 = new SampleResult();
        Map<String, String> listener2 = new HashMap<>();
        listener2.put("config22", "md51123");
        listener2.put("config2", "md5123123");
        sampleResult2.setLisentersGroupkeyStatus(listener2);
        List<SampleResult> sampleResults = new ArrayList<>();
        sampleResults.add(sampleResult2);
        SampleResult sampleResult3 = new SampleResult();
        Map<String, String> listener3 = new HashMap<>();
        listener3.put("config33", "md51123");
        listener3.put("config3", "md5123123");
        sampleResult3.setLisentersGroupkeyStatus(listener3);
        sampleResults.add(sampleResult3);
        //sampleResult ips is null
        SampleResult sampleResultMerge1 = configSubService.mergeSampleResult(sampleResult1, sampleResults);
        Assert.assertEquals(6, sampleResultMerge1.getLisentersGroupkeyStatus().size());
        
        SampleResult sampleResultMerge2 = configSubService.mergeSampleResult(new SampleResult(), sampleResults);
        Assert.assertEquals(4, sampleResultMerge2.getLisentersGroupkeyStatus().size());
    }
    
    @Test
    public void testMergeListenerCheckResult() throws Exception {
        ListenerCheckResult sampleResult2 = new ListenerCheckResult();
        sampleResult2.setHasListener(true);
        sampleResult2.setCode(200);
        List<ListenerCheckResult> sampleResults = new ArrayList<ListenerCheckResult>();
        sampleResults.add(sampleResult2);
        ListenerCheckResult sampleResult3 = new ListenerCheckResult();
        sampleResult3.setHasListener(false);
        sampleResult3.setCode(200);
        sampleResults.add(sampleResult3);
        ListenerCheckResult sampleResult1 = new ListenerCheckResult();
        //one ip return true
        ListenerCheckResult sampleResultMerge1 = configSubService.mergeListenerCheckResult(sampleResult1, sampleResults,
                2);
        Assert.assertEquals(200, sampleResultMerge1.getCode());
        Assert.assertEquals(true, sampleResultMerge1.isHasListener());
        //all ip return false,but not equals member size
        sampleResult2.setHasListener(false);
        sampleResult3.setHasListener(false);
        sampleResult1.setHasListener(false);
        ListenerCheckResult sampleResultMerge2 = configSubService.mergeListenerCheckResult(sampleResult1, sampleResults,
                3);
        Assert.assertEquals(201, sampleResultMerge2.getCode());
        Assert.assertEquals(false, sampleResultMerge2.isHasListener());
        
    }
    
    private Future<SampleResult> createSampleResultFuture(boolean success, boolean lisentersGroupkeyStatus) {
        Future<SampleResult> future = new Future<SampleResult>() {
            
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }
            
            @Override
            public boolean isCancelled() {
                return false;
            }
            
            @Override
            public boolean isDone() {
                return success ? true : false;
            }
            
            @Override
            public SampleResult get() {
                
                return success ? createSampleResult() : null;
            }
            
            @Override
            public SampleResult get(long timeout, TimeUnit unit) {
                return success ? createSampleResult() : null;
            }
            
            SampleResult createSampleResult() {
                SampleResult sampleResult = new SampleResult();
                if (lisentersGroupkeyStatus) {
                    Map<String, String> listener = new HashMap<>();
                    listener.put("config1", "md51123");
                    listener.put("config2", "md5123123");
                    sampleResult.setLisentersGroupkeyStatus(listener);
                }
                return sampleResult;
            }
        };
        return future;
    }
    
    Member createMember(String ip) {
        Member member = new Member();
        member.setIp(ip);
        member.setPort(8848);
        return member;
    }
}
