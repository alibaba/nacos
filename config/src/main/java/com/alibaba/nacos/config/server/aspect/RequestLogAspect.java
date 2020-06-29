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

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * * Created with IntelliJ IDEA. User: dingjoey Date: 13-12-12 Time: 21:12 client api && sdk api 请求日志打点逻辑
 *
 * @author Nacos
 */
@Aspect
@Component
public class RequestLogAspect {
    /**
     * publish config
     */
    private static final String CLIENT_INTERFACE_PUBLISH_SINGLE_CONFIG
        = "execution(* com.alibaba.nacos.config.server.controller.ConfigController.publishConfig(..)) && args"
        + "(request,response,dataId,group,tenant,content,..)";

    /**
     * get config
     */
    private static final String CLIENT_INTERFACE_GET_CONFIG
        = "execution(* com.alibaba.nacos.config.server.controller.ConfigController.getConfig(..)) && args(request,"
        + "response,dataId,group,tenant,..)";

    /**
     * remove config
     */
    private static final String CLIENT_INTERFACE_REMOVE_ALL_CONFIG
        = "execution(* com.alibaba.nacos.config.server.controller.ConfigController.deleteConfig(..)) && args(request,"
        + "response,dataId,group,tenant,..)";


    /**
     * publishSingle
     */
    @Around(CLIENT_INTERFACE_PUBLISH_SINGLE_CONFIG)
    public Object interfacePublishSingle(ProceedingJoinPoint pjp, HttpServletRequest request,
                                         HttpServletResponse response, String dataId, String group, String tenant,
                                         String content) throws Throwable {
        final String md5 = content == null ? null : MD5Utils.md5Hex(content, Constants.ENCODE);
        MetricsMonitor.getPublishMonitor().incrementAndGet();
        return logClientRequest("publish", pjp, request, response, dataId, group, tenant, md5);
    }

    /**
     * removeAll
     */
    @Around(CLIENT_INTERFACE_REMOVE_ALL_CONFIG)
    public Object interfaceRemoveAll(ProceedingJoinPoint pjp, HttpServletRequest request, HttpServletResponse response,
                                     String dataId, String group, String tenant) throws Throwable {
        return logClientRequest("remove", pjp, request, response, dataId, group, tenant, null);
    }

    /**
     * getConfig
     */
    @Around(CLIENT_INTERFACE_GET_CONFIG)
    public Object interfaceGetConfig(ProceedingJoinPoint pjp, HttpServletRequest request, HttpServletResponse response,
                                     String dataId, String group, String tenant) throws Throwable {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        final String md5 = ConfigCacheService.getContentMd5(groupKey);
        MetricsMonitor.getConfigMonitor().incrementAndGet();
        return logClientRequest("get", pjp, request, response, dataId, group, tenant, md5);
    }

    /**
     * client api request log rt | status | requestIp | opType | dataId | group | datumId | md5
     */
    private Object logClientRequest(String requestType, ProceedingJoinPoint pjp, HttpServletRequest request,
                                    HttpServletResponse response, String dataId, String group, String tenant,
                                    String md5) throws Throwable {
        final String requestIp = RequestUtil.getRemoteIp(request);
        String appName = request.getHeader(RequestUtil.CLIENT_APPNAME_HEADER);
        final long st = System.currentTimeMillis();
        Object retVal = pjp.proceed();
        final long rt = System.currentTimeMillis() - st;
        // rt | status | requestIp | opType | dataId | group | datumId | md5 |
        // appName
        LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}|{}|{}|{}", rt, retVal, requestIp, requestType, dataId, group, tenant,
            md5, appName);
        return retVal;
    }

}
