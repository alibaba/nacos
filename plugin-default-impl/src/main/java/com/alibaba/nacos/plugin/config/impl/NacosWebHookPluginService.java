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

package com.alibaba.nacos.plugin.config.impl;

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.config.server.service.notify.HttpClientManager;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.plugin.config.model.ConfigChangeNotifyInfo;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;
import com.alibaba.nacos.plugin.config.spi.AbstractWebHookPluginService;
import com.alibaba.nacos.plugin.config.util.ConfigChangeParamUtil;
import com.alibaba.nacos.plugin.config.util.ConfigPropertyUtil;
import org.apache.http.HttpStatus;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

/**
 * NacosWebHookPluginService.
 *
 * @author liyunfei
 */
public class NacosWebHookPluginService extends AbstractWebHookPluginService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosWebHookPluginService.class);
    
    private final NacosRestTemplate restTemplate = HttpClientManager.getNacosRestTemplate();
    
    private final Set<Integer> retryResponseCodes = new CopyOnWriteArraySet<Integer>(
            Arrays.asList(HttpStatus.SC_INTERNAL_SERVER_ERROR, HttpStatus.SC_BAD_GATEWAY,
                    HttpStatus.SC_SERVICE_UNAVAILABLE, HttpStatus.SC_GATEWAY_TIMEOUT));
    
    private static final int INCREASE_STEPS = 1000;
    
    private static final String WEBHOOK_URL = ConfigPropertyUtil.getWebHookUrl();
    
    private static final int MAX_CONTENT = ConfigPropertyUtil.getWebHookMaxContentCapacity();
    
    @Override
    public void execute(ProceedingJoinPoint pjp, ConfigChangeRequest configChangeRequest,
            ConfigChangeResponse configChangeResponse) {
        ConfigChangeNotifyInfo configChangeNotifyInfo = ConfigChangeParamUtil
                .convertRequestToNotifyInfo(configChangeRequest, configChangeResponse);
        String content = configChangeNotifyInfo.getContent();
        // check content length
        if (content != null) {
            if (content.length() > MAX_CONTENT) {
                configChangeNotifyInfo.setContent(content.substring(0, MAX_CONTENT));
            }
        }
        notifyConfigChange(configChangeNotifyInfo, WEBHOOK_URL);
    }
    
    @Override
    public void notifyConfigChange(ConfigChangeNotifyInfo configChangeNotifyInfo, String pushUrl) {
        ConfigExecutor.executeAsyncConfigChangePluginTask(new WebhookNotifySingleTask(pushUrl, configChangeNotifyInfo));
    }
    
    class WebhookNotifySingleTask implements Runnable {
        
        private String pushUrl;
        
        private ConfigChangeNotifyInfo configChangeNotifyInfo;
        
        private int retry = 0;
        
        private final int maxRetry = 6;
        
        public WebhookNotifySingleTask(String pushUrl, ConfigChangeNotifyInfo configChangeNotifyInfo) {
            this.pushUrl = pushUrl;
            this.configChangeNotifyInfo = configChangeNotifyInfo;
        }
        
        @Override
        public void run() {
            try {
                HttpRestResult<String> restResult = restTemplate
                        .post(pushUrl, Header.EMPTY, Query.EMPTY, configChangeNotifyInfo, String.class);
                int respCode = restResult.getCode();
                if (respCode != HttpStatus.SC_OK) {
                    if (!retryResponseCodes.contains(respCode)) {
                        LOGGER.warn(
                                "[{}]config change notify request failed,cause request params error,please check it",
                                getClass());
                    } else {
                        LOGGER.warn("config change notify request failed,will retry request {}",
                                restResult.getMessage());
                        retryRequest();
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedIOException || e instanceof UnknownHostException
                        || e instanceof ConnectException || e instanceof SSLException) {
                    LOGGER.warn("config change notify request failed,will retry request({}),cause: {}", retry,
                            e.getMessage());
                    retryRequest();
                } else {
                    LOGGER.warn("config change notify request failed,can not retry,case: {}", e.getMessage());
                }
            }
        }
        
        /**
         * Retry delay time.
         */
        private long getDelay() {
            return (long) retry * retry * INCREASE_STEPS;
        }
        
        private void retryRequest() {
            retry++;
            if (retry > maxRetry) {
                // Do not retry if over max retry count
                LOGGER.warn("retry to much,give up to push");
                return;
            }
            ConfigExecutor.scheduleAsyncConfigChangePluginTask(this, getDelay(), TimeUnit.MILLISECONDS);
        }
    }
}



