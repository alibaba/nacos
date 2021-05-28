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

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.service.notify.NotifyService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Config sub service.
 *
 * @author Nacos
 */
@Service
public class ConfigSubService {
    
    private ServerMemberManager memberManager;
    
    @Autowired
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public ConfigSubService(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
    }
    
    protected ConfigSubService() {
    
    }
    
    /**
     * Get and return called url string value.
     *
     * @param ip           ip.
     * @param relativePath path.
     * @return all path.
     */
    private String getUrl(String ip, String relativePath) {
        return "http://" + ip + EnvUtil.getContextPath() + relativePath;
    }
    
    private List<SampleResult> runCollectionJob(String url, Map<String, String> params,
            CompletionService<SampleResult> completionService, List<SampleResult> resultList) {
        
        Collection<Member> ipList = memberManager.allMembers();
        List<SampleResult> collectionResult = new ArrayList<SampleResult>(ipList.size());
        // Submit query task.
        for (Member ip : ipList) {
            try {
                completionService.submit(new Job(ip.getAddress(), url, params));
            } catch (Exception e) { // Send request failed.
                LogUtil.DEFAULT_LOG
                        .warn("Get client info from {} with exception: {} during submit job", ip, e.getMessage());
            }
        }
        // Get and merge result.
        SampleResult sampleResults = null;
        for (Member member : ipList) {
            try {
                Future<SampleResult> f = completionService.poll(1000, TimeUnit.MILLISECONDS);
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
            } catch (InterruptedException e) {
                LogUtil.DEFAULT_LOG.warn("get task result with InterruptedException: {} ", e.getMessage());
            } catch (ExecutionException e) {
                LogUtil.DEFAULT_LOG.warn("get task result with ExecutionException: {} ", e.getMessage());
            }
        }
        return collectionResult;
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
        Map<String, String> listenersGroupkeyStatus = null;
        if (sampleCollectResult.getLisentersGroupkeyStatus() == null || sampleCollectResult.getLisentersGroupkeyStatus()
                .isEmpty()) {
            listenersGroupkeyStatus = new HashMap<String, String>(10);
        } else {
            listenersGroupkeyStatus = sampleCollectResult.getLisentersGroupkeyStatus();
        }
        
        for (SampleResult sampleResult : sampleResults) {
            Map<String, String> listenersGroupkeyStatusTmp = sampleResult.getLisentersGroupkeyStatus();
            for (Map.Entry<String, String> entry : listenersGroupkeyStatusTmp.entrySet()) {
                listenersGroupkeyStatus.put(entry.getKey(), entry.getValue());
            }
        }
        mergeResult.setLisentersGroupkeyStatus(listenersGroupkeyStatus);
        return mergeResult;
    }
    
    /**
     * Query subscriber's task from every nacos server nodes.
     *
     * @author Nacos
     */
    class Job implements Callable<SampleResult> {
        
        private String ip;
        
        private String url;
        
        private Map<String, String> params;
        
        public Job(String ip, String url, Map<String, String> params) {
            this.ip = ip;
            this.url = url;
            this.params = params;
        }
        
        @Override
        public SampleResult call() throws Exception {
            
            try {
                StringBuilder paramUrl = new StringBuilder();
                for (Map.Entry<String, String> param : params.entrySet()) {
                    paramUrl.append("&").append(param.getKey()).append("=")
                            .append(URLEncoder.encode(param.getValue(), Constants.ENCODE));
                }
                
                String urlAll = getUrl(ip, url) + "?" + paramUrl;
                RestResult<String> result = NotifyService.invokeURL(urlAll, null, Constants.ENCODE);
                
                // Http code 200
                if (result.ok()) {
                    return JacksonUtils.toObj(result.getData(), SampleResult.class);
                } else {
                    
                    LogUtil.DEFAULT_LOG.info("Can not get clientInfo from {} with {}", ip, result.getData());
                    return null;
                }
            } catch (Exception e) {
                LogUtil.DEFAULT_LOG.warn("Get client info from {} with exception: {}", ip, e.getMessage());
                return null;
            }
        }
    }
    
    public SampleResult getCollectSampleResult(String dataId, String group, String tenant, int sampleTime)
            throws Exception {
        List<SampleResult> resultList = new ArrayList<SampleResult>();
        String url = Constants.COMMUNICATION_CONTROLLER_PATH + "/configWatchers";
        Map<String, String> params = new HashMap<String, String>(5);
        params.put("dataId", dataId);
        params.put("group", group);
        if (!StringUtils.isBlank(tenant)) {
            params.put("tenant", tenant);
        }
        BlockingQueue<Future<SampleResult>> queue = new LinkedBlockingDeque<Future<SampleResult>>(
                memberManager.getServerList().size());
        CompletionService<SampleResult> completionService = new ExecutorCompletionService<SampleResult>(
                ConfigExecutor.getConfigSubServiceExecutor(), queue);
        
        SampleResult sampleCollectResult = new SampleResult();
        for (int i = 0; i < sampleTime; i++) {
            List<SampleResult> sampleResults = runCollectionJob(url, params, completionService, resultList);
            sampleCollectResult = mergeSampleResult(sampleCollectResult, sampleResults);
        }
        return sampleCollectResult;
    }
    
    public SampleResult getCollectSampleResultByIp(String ip, int sampleTime) throws Exception {
        List<SampleResult> resultList = new ArrayList<SampleResult>(10);
        String url = Constants.COMMUNICATION_CONTROLLER_PATH + "/watcherConfigs";
        Map<String, String> params = new HashMap<String, String>(50);
        params.put("ip", ip);
        BlockingQueue<Future<SampleResult>> queue = new LinkedBlockingDeque<Future<SampleResult>>(
                memberManager.getServerList().size());
        CompletionService<SampleResult> completionService = new ExecutorCompletionService<SampleResult>(
                ConfigExecutor.getConfigSubServiceExecutor(), queue);
        
        SampleResult sampleCollectResult = new SampleResult();
        for (int i = 0; i < sampleTime; i++) {
            List<SampleResult> sampleResults = runCollectionJob(url, params, completionService, resultList);
            sampleCollectResult = mergeSampleResult(sampleCollectResult, sampleResults);
        }
        return sampleCollectResult;
    }
    
}
