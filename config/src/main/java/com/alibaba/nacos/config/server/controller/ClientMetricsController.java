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

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest;
import com.alibaba.nacos.api.config.remote.response.ClientConfigMetricResponse;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest.MetricsKey.CACHE_DATA;
import static com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest.MetricsKey.SNAPSHOT_DATA;

/**
 * ClientMetricsController.
 *
 * @author zunfei.lzf
 */
@RestController
@RequestMapping(Constants.METRICS_CONTROLLER_PATH)
public class ClientMetricsController {
    
    @Autowired
    private ServerMemberManager serverMemberManager;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    /**
     * get client metric.
     *
     * @param ip client ip .
     * @return
     */
    @GetMapping("/cluster")
    public ResponseEntity metric(@RequestParam("ip") String ip,
            @RequestParam(value = "dataId", required = false) String dataId,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "tenant", required = false) String tenant) {
        Loggers.CORE.info("Get cluster config metrics received, ip={},dataId={},group={},tenant={}", ip, dataId, group,
                tenant);
        Map<String, Object> responseMap = new HashMap<>(3);
        Collection<Member> members = serverMemberManager.allMembers();
        final NacosAsyncRestTemplate nacosAsyncRestTemplate = HttpClientBeanHolder
                .getNacosAsyncRestTemplate(Loggers.CLUSTER);
        CountDownLatch latch = new CountDownLatch(members.size());
        for (Member member : members) {
            String url = HttpUtils
                    .buildUrl(false, member.getAddress(), EnvUtil.getContextPath(), Constants.METRICS_CONTROLLER_PATH,
                            "current");
            Query query = Query.newInstance().addParam("ip", ip).addParam("dataId", dataId).addParam("group", group)
                    .addParam("tenant", tenant);
            nacosAsyncRestTemplate.get(url, Header.EMPTY, query, new GenericType<Map>() {
            }.getType(), new Callback<Map>() {
                
                @Override
                public void onReceive(RestResult<Map> result) {
                    if (result.ok()) {
                        responseMap.putAll(result.getData());
                    }
                    latch.countDown();
                }
                
                @Override
                public void onError(Throwable throwable) {
                    Loggers.CORE
                            .error("Get config metrics error from member address={}, ip={},dataId={},group={},tenant={},error={}",
                                    member.getAddress(), ip, dataId, group, tenant, throwable);
                    latch.countDown();
                }
                
                @Override
                public void onCancel() {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(3L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        return ResponseEntity.ok().body(responseMap);
    }
    
    
    /**
     * Get client config listener lists of subscriber in local machine.
     */
    @GetMapping("/current")
    public Map<String, Object> getClientMetrics(@RequestParam("ip") String ip,
            @RequestParam(value = "dataId", required = false) String dataId,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "tenant", required = false) String tenant) {
        Map<String, Object> metrics = new HashMap<>(16);
        List<Connection> connectionsByIp = connectionManager.getConnectionByIp(ip);
        for (Connection connectionByIp : connectionsByIp) {
            try {
                ClientConfigMetricRequest clientMetrics = new ClientConfigMetricRequest();
                if (StringUtils.isNotBlank(dataId)) {
                    clientMetrics.getMetricsKeys().add(ClientConfigMetricRequest.MetricsKey
                            .build(CACHE_DATA, GroupKey2.getKey(dataId, group, tenant)));
                    clientMetrics.getMetricsKeys().add(ClientConfigMetricRequest.MetricsKey
                            .build(SNAPSHOT_DATA, GroupKey2.getKey(dataId, group, tenant)));
                }
                
                ClientConfigMetricResponse request1 = (ClientConfigMetricResponse) connectionByIp
                        .request(clientMetrics, 1000L);
                metrics.putAll(request1.getMetrics());
            } catch (Exception e) {
                Loggers.CORE.error("Get config metrics error from client ip={},dataId={},group={},tenant={},error={}", ip, dataId,
                        group, tenant, e);
            }
        }
        return metrics;
        
    }
    
}
