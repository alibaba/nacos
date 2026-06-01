/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest;
import com.alibaba.nacos.api.config.remote.response.ClientConfigMetricResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.auth.util.AuthHeaderUtil;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest.MetricsKey.CACHE_DATA;
import static com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest.MetricsKey.SNAPSHOT_DATA;

/**
 * Metric management.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.METRICS_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class MetricsControllerV3 {
    
    private final ServerMemberManager serverMemberManager;
    
    private final ConnectionManager connectionManager;
    
    public MetricsControllerV3(ServerMemberManager serverMemberManager,
        ConnectionManager connectionManager) {
        this.serverMemberManager = serverMemberManager;
        this.connectionManager = connectionManager;
    }
    
    /**
     * get client metric.
     */
    @Since("3.0.0")
    @GetMapping("/cluster")
    @Secured(resource = Constants.METRICS_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
        signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Map<String, Object>> metric(@RequestParam("ip") String ip,
        @RequestParam(value = "dataId", required = false) String dataId,
        @RequestParam(value = "groupName", required = false) String groupName,
        @RequestParam(value = "namespaceId", required = false) String namespaceId)
        throws NacosException {
        
        ParamUtils.checkTenant(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        ParamUtils.checkParam(dataId, groupName, "default", "default");
        
        Loggers.CORE.info(
            "Get cluster config metrics received, ip={},dataId={},groupName={},namespaceId={}", ip,
            dataId, groupName, namespaceId);
        Map<String, Object> responseMap = new HashMap<>(3);
        AtomicBoolean complete = new AtomicBoolean(true);
        Collection<Member> members = serverMemberManager.allMembers();
        final NacosAsyncRestTemplate nacosAsyncRestTemplate =
            HttpClientBeanHolder.getNacosAsyncRestTemplate(
                Loggers.CLUSTER);
        CountDownLatch latch = new CountDownLatch(members.size());
        for (Member member : members) {
            String url = HttpUtils.buildUrl(false, member.getAddress(), EnvUtil.getContextPath(),
                Constants.METRICS_CONTROLLER_V3_ADMIN_PATH, "ip");
            Query query = Query.newInstance().addParam("ip", ip).addParam("dataId", dataId)
                .addParam("groupName", groupName).addParam("namespaceId", namespaceId);
            Header header = Header.newInstance();
            AuthHeaderUtil.addIdentityToHeader(header, NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE));
            nacosAsyncRestTemplate.get(url, header, query, new GenericType<Map>() {
            }.getType(),
                new ClusterMetricsCallBack(responseMap, latch, complete, dataId, groupName,
                    namespaceId, ip, member));
        }
        try {
            boolean completed = latch.await(3L, TimeUnit.SECONDS);
            if (!completed) {
                complete.set(false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Loggers.CORE.warn(
                "Interrupted while waiting cluster config metrics, ip={},dataId={},groupName={},namespaceId={}",
                ip, dataId, groupName, namespaceId, e);
            complete.set(false);
        }
        responseMap.put("complete", complete.get());
        
        return Result.success(responseMap);
    }
    
    static class ClusterMetricsCallBack implements Callback<Map> {
        
        Map<String, Object> responseMap;
        
        CountDownLatch latch;
        
        AtomicBoolean complete;
        
        String dataId;
        
        String group;
        
        String namespaceId;
        
        String ip;
        
        Member member;
        
        public ClusterMetricsCallBack(Map<String, Object> responseMap, CountDownLatch latch,
            AtomicBoolean complete,
            String dataId,
            String group, String namespaceId, String ip, Member member) {
            this.responseMap = responseMap;
            this.latch = latch;
            this.complete = complete;
            this.dataId = dataId;
            this.group = group;
            this.namespaceId = namespaceId;
            this.member = member;
            this.ip = ip;
        }
        
        @Override
        public void onReceive(RestResult<Map> result) {
            if (result != null && result.ok() && result.getData() != null) {
                responseMap.putAll(result.getData());
            } else {
                complete.set(false);
            }
            latch.countDown();
        }
        
        @Override
        public void onError(Throwable throwable) {
            complete.set(false);
            Loggers.CORE.error(
                "Get config metrics error from member address={}, ip={},dataId={},group={},namespaceId={},error={}",
                member.getAddress(), ip, dataId, group, namespaceId, throwable);
            latch.countDown();
        }
        
        @Override
        public void onCancel() {
            complete.set(false);
            latch.countDown();
        }
    }
    
    /**
     * Get client config listener lists of subscriber in local machine.
     */
    @Since("3.0.0")
    @GetMapping("/ip")
    @Secured(resource = Constants.METRICS_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
        signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Map<String, Object>> getClientMetrics(@RequestParam("ip") String ip,
        @RequestParam(value = "dataId", required = false) String dataId,
        @RequestParam(value = "groupName", required = false) String groupName,
        @RequestParam(value = "namespaceId", required = false) String namespaceId)
        throws NacosException {
        
        ParamUtils.checkTenant(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        ParamUtils.checkParam(dataId, groupName, "default", "default");
        
        Map<String, Object> metrics = new HashMap<>(16);
        List<Connection> connectionsByIp = connectionManager.getConnectionByIp(ip);
        for (Connection connectionByIp : connectionsByIp) {
            try {
                ClientConfigMetricRequest clientMetrics = new ClientConfigMetricRequest();
                if (StringUtils.isNotBlank(dataId)) {
                    clientMetrics.getMetricsKeys()
                        .add(ClientConfigMetricRequest.MetricsKey.build(CACHE_DATA,
                            GroupKey2.getKey(dataId, groupName, namespaceId)));
                    clientMetrics.getMetricsKeys()
                        .add(ClientConfigMetricRequest.MetricsKey.build(SNAPSHOT_DATA,
                            GroupKey2.getKey(dataId, groupName, namespaceId)));
                }
                
                ClientConfigMetricResponse request1 =
                    (ClientConfigMetricResponse) connectionByIp.request(clientMetrics,
                        1000L);
                metrics.putAll(request1.getMetrics());
            } catch (Exception e) {
                Loggers.CORE.error(
                    "Get config metrics error from client ip={},dataId={},groupName={},namespaceId={},error={}",
                    ip,
                    dataId, groupName, namespaceId, e);
                throw new NacosException(NacosException.SERVER_ERROR, e);
            }
        }
        
        return Result.success(metrics);
    }
}
