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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ListenerCheckResult;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.service.notify.HttpClientManager;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;

/**
 * Config sub service.
 *
 * @author Nacos
 */
@Service
public class ConfigSubService {
    
    private ServerMemberManager memberManager;
    
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public ConfigSubService(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }
    
    /**
     * Get and return called url string value.
     *
     * @param ip           ip.
     * @param relativePath path.
     * @return all path.
     */
    private static String getUrl(String ip, String relativePath) {
        return HTTP_PREFIX + ip + EnvUtil.getContextPath() + relativePath;
    }
    
    private List<SampleResult> runConfigListenerCollectionJob(Map<String, String> params,
            CompletionService<SampleResult> completionService) {
        return new ClusterListenerJob(params, completionService, memberManager).runJobs();
    }
    
    private List<SampleResult> runConfigListenerByIpCollectionJob(Map<String, String> params,
            CompletionService<SampleResult> completionService) {
        return new ClusterListenerByIpJob(params, completionService, memberManager).runJobs();
    }
    
    static class ClusterListenerJob extends ClusterJob<SampleResult> {
        
        static final String URL = Constants.COMMUNICATION_CONTROLLER_PATH + "/configWatchers";
        
        ClusterListenerJob(Map<String, String> params, CompletionService<SampleResult> completionService,
                ServerMemberManager serverMemberManager) {
            super(URL, params, completionService, serverMemberManager);
        }
    }
    
    static class ClusterListenerByIpJob extends ClusterJob<SampleResult> {
        
        static final String URL = Constants.COMMUNICATION_CONTROLLER_PATH + "/watcherConfigs";
        
        ClusterListenerByIpJob(Map<String, String> params, CompletionService<SampleResult> completionService,
                ServerMemberManager serverMemberManager) {
            super(URL, params, completionService, serverMemberManager);
        }
    }
    
    private List<ListenerCheckResult> runHasCheckListenerCollectionJob(Map<String, String> params,
            CompletionService<ListenerCheckResult> completionService) {
        return new ClusterCheckHasListenerJob(params, completionService, memberManager).runJobs();
    }
    
    class ClusterCheckHasListenerJob extends ClusterJob<ListenerCheckResult> {
        
        static final String URL = Constants.COMMUNICATION_CONTROLLER_PATH + "/checkConfigWatchers";
        
        ClusterCheckHasListenerJob(Map<String, String> params, CompletionService<ListenerCheckResult> completionService,
                ServerMemberManager serverMemberManager) {
            super(URL, params, completionService, serverMemberManager);
        }
    }
    
    @SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
    abstract static class ClusterJob<T> {
        
        private String url;
        
        private Map<String, String> params;
        
        private CompletionService<T> completionService;
        
        private ServerMemberManager serverMemberManager;
        
        ClusterJob(String url, Map<String, String> params, CompletionService<T> completionService,
                ServerMemberManager serverMemberManager) {
            this.url = url;
            this.params = params;
            this.completionService = completionService;
            this.serverMemberManager = serverMemberManager;
        }
        
        class Job<T> implements Callable<T> {
            
            private String ip;
            
            public Job(String ip) {
                this.ip = ip;
            }
            
            @Override
            public T call() throws Exception {
                return (T) runSingleJob(ip, params, url, ((ParameterizedType) ClusterJob.this.getClass()
                        .getGenericSuperclass()).getActualTypeArguments()[0]);
            }
        }
        
        List<T> runJobs() {
            Collection<Member> ipList = serverMemberManager.allMembers();
            List<T> collectionResult = new ArrayList<>(ipList.size());
            // Submit query task.
            for (Member ip : ipList) {
                try {
                    completionService.submit(new Job<T>(ip.getAddress()) {
                    });
                } catch (Exception e) { // Send request failed.
                    LogUtil.DEFAULT_LOG.warn("invoke to {} with exception: {} during submit job", ip, e.getMessage());
                }
            }
            // Get and merge result.
            T sampleResults;
            for (Member member : ipList) {
                try {
                    Future<T> f = completionService.poll(1000, TimeUnit.MILLISECONDS);
                    try {
                        if (f != null) {
                            sampleResults = f.get(500, TimeUnit.MILLISECONDS);
                            if (sampleResults != null) {
                                collectionResult.add(sampleResults);
                            }
                        } else {
                            LogUtil.DEFAULT_LOG.warn("The task in ip: {}  did not completed in 1000ms ", member);
                        }
                    } catch (TimeoutException e) {
                        if (f != null) {
                            f.cancel(true);
                        }
                        LogUtil.DEFAULT_LOG.warn("get task result with TimeoutException: {} ", e.getMessage());
                    }
                } catch (Exception e) {
                    LogUtil.DEFAULT_LOG.warn("get task result with Exception: {} ", e.getMessage());
                }
            }
            return collectionResult;
        }
    }
    
    /**
     * run job to a single member.
     *
     * @param ip     ip.
     * @param params params.
     * @param url    url.
     * @param type   type.
     * @return
     */
    public static Object runSingleJob(String ip, Map<String, String> params, String url, Type type) {
        try {
            StringBuilder paramUrl = new StringBuilder();
            for (Map.Entry<String, String> param : params.entrySet()) {
                paramUrl.append("&").append(param.getKey()).append("=")
                        .append(URLEncoder.encode(param.getValue(), Constants.ENCODE_UTF8));
            }
            
            String urlAll = getUrl(ip, url) + "?" + paramUrl;
            RestResult<String> result = invokeUrl(urlAll, Constants.ENCODE_UTF8);
            // Http code 200
            if (result.ok()) {
                Object t = JacksonUtils.toObj(result.getData(), type);
                return t;
            } else {
                LogUtil.DEFAULT_LOG.info("Can not get remote from {} with {}", ip, result.getData());
                return null;
            }
        } catch (Exception e) {
            LogUtil.DEFAULT_LOG.warn("Get remote info from {} with exception: {}", ip, e.getMessage());
            return null;
        }
    }
    
    public ListenerCheckResult getCheckHasListenerResult(String dataId, String group, String tenant, int sampleTime)
            throws Exception {
        Map<String, String> params = new HashMap<>(5);
        params.put("dataId", dataId);
        params.put("group", group);
        if (!StringUtils.isBlank(tenant)) {
            params.put("tenant", tenant);
        }
        int size = memberManager.getServerList().size();
        BlockingQueue<Future<ListenerCheckResult>> queue = new LinkedBlockingDeque<>(
                memberManager.getServerList().size());
        CompletionService<ListenerCheckResult> completionService = new ExecutorCompletionService<>(
                ConfigExecutor.getConfigSubServiceExecutor(), queue);
        
        ListenerCheckResult sampleCollectResult = new ListenerCheckResult();
        sampleCollectResult.setCode(201);
        for (int i = 0; i < sampleTime; i++) {
            List<ListenerCheckResult> sampleResults = runHasCheckListenerCollectionJob(params, completionService);
            if (sampleResults != null) {
                sampleCollectResult = mergeListenerCheckResult(sampleCollectResult, sampleResults, size);
            }
            if (sampleCollectResult.isHasListener()) {
                break;
            }
            
        }
        
        return sampleCollectResult;
    }
    
    /**
     * if has all server has not listener,return false.
     *
     * @param listenerCheckResult listenerCheckResult.
     * @param sampleResults       sampleResults.
     * @return
     */
    public ListenerCheckResult mergeListenerCheckResult(ListenerCheckResult listenerCheckResult,
            List<ListenerCheckResult> sampleResults, int expectSize) {
        for (ListenerCheckResult sampleResult : sampleResults) {
            if (sampleResult.getCode() == 200 && sampleResult.isHasListener()) {
                listenerCheckResult.setHasListener(true);
                listenerCheckResult.setCode(200);
                break;
            }
        }
        if (!listenerCheckResult.isHasListener() && sampleResults.size() != expectSize) {
            listenerCheckResult.setCode(201);
        }
        
        return listenerCheckResult;
    }
    
    /**
     * Merge SampleResult.
     *
     * @param sampleCollectResult sampleCollectResult.
     * @param sampleResults       sampleResults.
     * @return SampleResult.
     */
    public SampleResult mergeSampleResult(SampleResult sampleCollectResult, List<SampleResult> sampleResults) {
        SampleResult mergeResult = new SampleResult();
        Map<String, String> listenersGroupkeyStatus;
        if (sampleCollectResult.getLisentersGroupkeyStatus() == null || sampleCollectResult.getLisentersGroupkeyStatus()
                .isEmpty()) {
            listenersGroupkeyStatus = new HashMap<>(10);
        } else {
            listenersGroupkeyStatus = sampleCollectResult.getLisentersGroupkeyStatus();
        }
        
        for (SampleResult sampleResult : sampleResults) {
            Map<String, String> listenersGroupkeyStatusTmp = sampleResult.getLisentersGroupkeyStatus();
            listenersGroupkeyStatus.putAll(listenersGroupkeyStatusTmp);
        }
        mergeResult.setLisentersGroupkeyStatus(listenersGroupkeyStatus);
        return mergeResult;
    }
    
    public SampleResult getCollectSampleResult(String dataId, String group, String tenant, int sampleTime)
            throws Exception {
        Map<String, String> params = new HashMap<>(5);
        params.put("dataId", dataId);
        params.put("group", group);
        if (!StringUtils.isBlank(tenant)) {
            params.put("tenant", tenant);
        }
        BlockingQueue<Future<SampleResult>> queue = new LinkedBlockingDeque<>(memberManager.getServerList().size());
        CompletionService<SampleResult> completionService = new ExecutorCompletionService<>(
                ConfigExecutor.getConfigSubServiceExecutor(), queue);
        
        SampleResult sampleCollectResult = new SampleResult();
        for (int i = 0; i < sampleTime; i++) {
            List<SampleResult> sampleResults = runConfigListenerCollectionJob(params, completionService);
            if (sampleResults != null) {
                sampleCollectResult = mergeSampleResult(sampleCollectResult, sampleResults);
            }
        }
        return sampleCollectResult;
    }
    
    public SampleResult getCollectSampleResultByIp(String ip, int sampleTime) {
        Map<String, String> params = new HashMap<>(50);
        params.put("ip", ip);
        BlockingQueue<Future<SampleResult>> queue = new LinkedBlockingDeque<>(memberManager.getServerList().size());
        CompletionService<SampleResult> completionService = new ExecutorCompletionService<>(
                ConfigExecutor.getConfigSubServiceExecutor(), queue);
        
        SampleResult sampleCollectResult = new SampleResult();
        for (int i = 0; i < sampleTime; i++) {
            List<SampleResult> sampleResults = runConfigListenerByIpCollectionJob(params, completionService);
            if (sampleResults != null) {
                sampleCollectResult = mergeSampleResult(sampleCollectResult, sampleResults);
            }
        }
        return sampleCollectResult;
    }
    
    /**
     * invoke url with http.
     *
     * @param url      url.
     * @param encoding encoding.
     * @return result.
     * @throws Exception exception.
     */
    public static RestResult<String> invokeUrl(String url, String encoding) throws Exception {
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.ACCEPT_CHARSET, encoding);
        return HttpClientManager.getNacosRestTemplate().get(url, header, Query.EMPTY, String.class);
    }
}
