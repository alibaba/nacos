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

package com.alibaba.nacos.config.server.aspect;

import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aspect for logging HTTP API and SDK API requests in Nacos.
 *
 * @author Nacos
 */
@Aspect
@Component
public class RequestLogAspect {
    
    private static final String PUBLISH_CONFIG =
            "execution(* com.alibaba.nacos.config.server.service.ConfigOperationService.publishConfig(..))";
    
    private static final String GET_CONFIG =
            "execution(* com.alibaba.nacos.config.server.service.query.ConfigQueryChainService.handle(..))";
    
    private static final String DELETE_CONFIG =
            "execution(* com.alibaba.nacos.config.server.service.ConfigOperationService.deleteConfig(..))";
    
    private static final String CONFIG_CHANGE_LISTEN_RPC =
            "execution(* com.alibaba.nacos.core.remote.RequestHandler.handleRequest(..)) "
                    + " && target(com.alibaba.nacos.config.server.remote.ConfigChangeBatchListenRequestHandler) && args(request,meta)";
    
    /**
     * Intercepts configuration publishing operations, records metrics, and logs client requests.
     */
    @Around(PUBLISH_CONFIG)
    public Object interfacePublishConfig(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        ConfigForm configForm = (ConfigForm) args[0];
        ConfigRequestInfo configRequestInfo = (ConfigRequestInfo) args[1];
        String dataId = configForm.getDataId();
        String group = configForm.getGroup();
        String namespaceId = configForm.getNamespaceId();
        String content = configForm.getContent();
        String requestIp = configRequestInfo.getSrcIp();
        String md5 = content == null ? null : MD5Utils.md5Hex(content, Constants.ENCODE);
        
        MetricsMonitor.getPublishMonitor().incrementAndGet();
        AtomicLong rtHolder = new AtomicLong();
        Object retVal = logClientRequest("publish", pjp, dataId, group, namespaceId, requestIp, md5, rtHolder);
        MetricsMonitor.getWriteConfigRtTimer().record(rtHolder.get(), TimeUnit.MILLISECONDS);
        
        return retVal;
    }
    
    /**
     * Intercepts configuration get operations, records metrics, and logs client requests.
     */
    @Around(GET_CONFIG)
    public Object interfaceGetConfig(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        ConfigQueryChainRequest chainRequest = (ConfigQueryChainRequest) args[0];
        String dataId = chainRequest.getDataId();
        String group = chainRequest.getGroup();
        String tenant = chainRequest.getTenant();
        String requestIp = null;
        if (chainRequest.getAppLabels() != null) {
            requestIp = chainRequest.getAppLabels().getOrDefault(BetaGrayRule.CLIENT_IP_LABEL, null);
        }
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String md5 = ConfigCacheService.getContentMd5(groupKey);
        
        MetricsMonitor.getConfigMonitor().incrementAndGet();
        AtomicLong rtHolder = new AtomicLong();
        Object retVal = logClientRequest("get", pjp, dataId, group, tenant, requestIp, md5, rtHolder);
        MetricsMonitor.getReadConfigRtTimer().record(rtHolder.get(), TimeUnit.MILLISECONDS);
        
        return retVal;
    }
    
    /**
     * Deletes a configuration entry and logs the operation.
     */
    @Around(DELETE_CONFIG)
    public Object interfaceRemoveConfig(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        String dataId = (String) args[0];
        String group = (String) args[1];
        String tenant = (String) args[2];
        String clientIp = (String) args[4];
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String md5 = ConfigCacheService.getContentMd5(groupKey);
        
        MetricsMonitor.getConfigMonitor().incrementAndGet();
        AtomicLong rtHolder = new AtomicLong();
        Object retVal = logClientRequest("delete", pjp, dataId, group, tenant, clientIp, md5, rtHolder);
        MetricsMonitor.getReadConfigRtTimer().record(rtHolder.get(), TimeUnit.MILLISECONDS);
        
        return retVal;
    }
    
    /**
     * Client api request log rt | status | requestIp | opType | dataId | group | datumId | md5.
     */
    private Object logClientRequest(String requestType, ProceedingJoinPoint pjp, String dataId, String group,
            String tenant, String requestIp, String md5, AtomicLong rtHolder) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            Object retVal = pjp.proceed();
            
            long rt = System.currentTimeMillis() - startTime;
            if (rtHolder != null) {
                rtHolder.set(rt);
            }
            
            LogUtil.CLIENT_LOG.info("opType: {} | rt: {}ms | status: success | requestIp: {} | dataId: {} | group: {} | tenant: {} | md5: {}",
                    requestType, rt, requestIp, dataId, group, tenant, md5);
            
            return retVal;
            
        } catch (Throwable e) {
            long rt = System.currentTimeMillis() - startTime;
            if (rtHolder != null) {
                rtHolder.set(rt);
            }
            
            LogUtil.CLIENT_LOG.error("opType: {} | rt: {}ms | status: failure | requestIp: {} | dataId: {} | group: {} | tenant: {} | md5: {}",
                    requestType, rt, requestIp, dataId, group, tenant, md5);
            
            throw e;
        }
    }
    
    /**
     * Handles configuration change listening requests.
     */
    @Around(CONFIG_CHANGE_LISTEN_RPC)
    public Object interfaceListenConfigRpc(ProceedingJoinPoint pjp, ConfigBatchListenRequest request,
            RequestMeta meta) throws Throwable {
        MetricsMonitor.getConfigMonitor().incrementAndGet();
        final String requestIp = meta.getClientIp();
        String appName = request.getHeader(RequestUtil.CLIENT_APPNAME_HEADER);
        final long st = System.currentTimeMillis();
        Response retVal = (Response) pjp.proceed();
        final long rt = System.currentTimeMillis() - st;
        LogUtil.CLIENT_LOG.info("opType: {} | rt: {}ms | status: {} | requestIp: {} | listenSize: {} | listenOrCancel: {} | appName: {}", "listen",
                rt, retVal.isSuccess() ? retVal.getResultCode() : retVal.getErrorCode(), requestIp, request.getConfigListenContexts().size(),
                request.isListen(), appName);
        return retVal;
    }
}
