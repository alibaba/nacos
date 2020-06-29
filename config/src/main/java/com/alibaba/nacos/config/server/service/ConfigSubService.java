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

import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.service.notify.NotifyService;
import com.alibaba.nacos.config.server.utils.JSONUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * config sub service
 *
 * @author Nacos
 */
@Service
public class ConfigSubService {

    private ScheduledExecutorService scheduler;

    private ServerMemberManager memberManager;

    @Autowired
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public ConfigSubService(ServerMemberManager memberManager) {
        this.memberManager = memberManager;

        scheduler = Executors.newScheduledThreadPool(
            ThreadUtils.getSuitableThreadCount(), new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("com.alibaba.nacos.ConfigSubService");
                    return t;
                }
            });
    }

    protected ConfigSubService() {

    }

    /**
     * 获得调用的URL
     *
     * @param ip           ip
     * @param relativePath path
     * @return all path
     */
    private String getUrl(String ip, String relativePath) {
        return "http://" + ip + ApplicationUtils.getContextPath() + relativePath;
    }

    private List<SampleResult> runCollectionJob(String url, Map<String, String> params,
                                                CompletionService<SampleResult> completionService,
                                                List<SampleResult> resultList) {

        Collection<Member> ipList = memberManager.allMembers();
        List<SampleResult> collectionResult = new ArrayList<SampleResult>(
            ipList.size());
        // 提交查询任务
        for (Member ip : ipList) {
            try {
                completionService.submit(new Job(ip.getAddress(), url, params));
            } catch (Exception e) { // 发送请求失败
                LogUtil.defaultLog
                    .warn("Get client info from {} with exception: {} during submit job",
                        ip, e.getMessage());
            }
        }
        // 获取结果并合并
        SampleResult sampleResults = null;
        for (Member member : ipList) {
            try {
                Future<SampleResult> f = completionService.poll(1000,
                    TimeUnit.MILLISECONDS);
                try {
                    if (f != null) {
                        sampleResults = f.get(500, TimeUnit.MILLISECONDS);
                        if (sampleResults != null) {
                            collectionResult.add(sampleResults);
                        }
                    } else {
                        LogUtil.defaultLog
                            .warn("The task in ip: {}  did not completed in 1000ms ",
                                member);
                    }
                } catch (TimeoutException e) {
                    if (f != null) {
                        f.cancel(true);
                    }
                    LogUtil.defaultLog.warn(
                        "get task result with TimeoutException: {} ", e
                            .getMessage());
                }
            } catch (InterruptedException e) {
                LogUtil.defaultLog.warn(
                    "get task result with InterruptedException: {} ", e
                        .getMessage());
            } catch (ExecutionException e) {
                LogUtil.defaultLog.warn(
                    "get task result with ExecutionException: {} ", e
                        .getMessage());
            }
        }
        return collectionResult;
    }

    public SampleResult mergeSampleResult(SampleResult sampleCollectResult, List<SampleResult> sampleResults) {
        SampleResult mergeResult = new SampleResult();
        Map<String, String> lisentersGroupkeyStatus = null;
        if (sampleCollectResult.getLisentersGroupkeyStatus() == null
            || sampleCollectResult.getLisentersGroupkeyStatus().isEmpty()) {
            lisentersGroupkeyStatus = new HashMap<String, String>(10);
        } else {
            lisentersGroupkeyStatus = sampleCollectResult.getLisentersGroupkeyStatus();
        }

        for (SampleResult sampleResult : sampleResults) {
            Map<String, String> lisentersGroupkeyStatusTmp = sampleResult.getLisentersGroupkeyStatus();
            for (Map.Entry<String, String> entry : lisentersGroupkeyStatusTmp.entrySet()) {
                lisentersGroupkeyStatus.put(entry.getKey(), entry.getValue());
            }
        }
        mergeResult.setLisentersGroupkeyStatus(lisentersGroupkeyStatus);
        return mergeResult;
    }

    /**
     * 去每个Nacos Server节点查询订阅者的任务
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
                com.alibaba.nacos.config.server.service.notify.NotifyService.HttpResult result = NotifyService
                    .invokeURL(urlAll, null, Constants.ENCODE);
                /**
                 *  http code 200
                 */
                if (result.code == HttpURLConnection.HTTP_OK) {
                    String json = result.content;
                    SampleResult resultObj = JSONUtils.deserializeObject(json,
                        new TypeReference<SampleResult>() {
                        });
                    return resultObj;

                } else {

                    LogUtil.defaultLog.info(
                        "Can not get clientInfo from {} with {}", ip,
                        result.code);
                    return null;
                }
            } catch (Exception e) {
                LogUtil.defaultLog.warn(
                    "Get client info from {} with exception: {}", ip, e
                        .getMessage());
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
        CompletionService<SampleResult> completionService = new ExecutorCompletionService<SampleResult>(scheduler,
            queue);

        SampleResult sampleCollectResult = new SampleResult();
        for (int i = 0; i < sampleTime; i++) {
            List<SampleResult> sampleResults = runCollectionJob(url, params, completionService, resultList);
            if (sampleResults != null) {
                sampleCollectResult = mergeSampleResult(sampleCollectResult, sampleResults);
            }
        }
        return sampleCollectResult;
    }

    public SampleResult getCollectSampleResultByIp(String ip, int sampleTime)
        throws Exception {
        List<SampleResult> resultList = new ArrayList<SampleResult>(10);
        String url = Constants.COMMUNICATION_CONTROLLER_PATH + "/watcherConfigs";
        Map<String, String> params = new HashMap<String, String>(50);
        params.put("ip", ip);
        BlockingQueue<Future<SampleResult>> queue = new LinkedBlockingDeque<Future<SampleResult>>(
            memberManager.getServerList().size());
        CompletionService<SampleResult> completionService = new ExecutorCompletionService<SampleResult>(scheduler,
            queue);

        SampleResult sampleCollectResult = new SampleResult();
        for (int i = 0; i < sampleTime; i++) {
            List<SampleResult> sampleResults = runCollectionJob(url, params, completionService, resultList);
            if (sampleResults != null) {
                sampleCollectResult = mergeSampleResult(sampleCollectResult, sampleResults);
            }
        }
        return sampleCollectResult;
    }

}
