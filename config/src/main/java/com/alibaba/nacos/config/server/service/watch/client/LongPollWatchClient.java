/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.config.server.service.watch.client;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.config.server.service.watch.WatchClient;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.core.utils.WebUtils;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.utils.LogUtil.PULL_LOG;

/**
 * HTTP long rotation configuration listening client
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class LongPollWatchClient extends WatchClient {
    
    private static final int FIXED_POLLING_INTERVAL_MS = 10000;
    
    private Future<?> asyncTimeoutFuture;
    
    private final AsyncContext context;
    
    private final int probeRequestSize;
    
    private final long timeoutTime;
    
    private final long createTime = System.currentTimeMillis();
    
    LongPollWatchClient(String appName, String address, String namespace, Map<String, String> watchKey,
            long timeoutTime, final AsyncContext context) {
        super(appName, address, namespace, watchKey);
        this.context = context;
        if (isFixedPolling()) {
            // Do nothing but set fix polling timeout.
            timeoutTime = Math.max(10000, getFixedPollingInterval());
        }
        this.timeoutTime = timeoutTime;
        String probeModify = WebUtils.required((HttpServletRequest) context.getRequest(), "Listening-Configs");
        if (StringUtils.isBlank(probeModify)) {
            throw new IllegalArgumentException("invalid probeModify");
        }
        this.probeRequestSize = WebUtils.resolveValueWithUrlDecode(probeModify, Constants.ENCODE).length();
    }
    
    @Override
    protected void init() {
        final HttpServletRequest req = (HttpServletRequest) context.getRequest();
        final HttpServletResponse resp = (HttpServletResponse) context.getResponse();
        if (!isFixedPolling()) {
            String noHangUpFlag = req.getHeader(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER);
            long start = System.currentTimeMillis();
            List<String> changedGroups = MD5Util.compareMd5(req, resp, getWatchKey());
            if (changedGroups.size() > 0) {
                clientManager.removeWatchClient(LongPollWatchClient.this);
                generateResponse(req, resp, changedGroups);
                LogUtil.CLIENT_LOG.info("{}|{}|{}|{}|{}|{}|{}", System.currentTimeMillis() - start, "instant",
                        RequestUtil.getRemoteIp(req), "polling", getWatchKey().size(), probeRequestSize,
                        changedGroups.size());
                return;
            } else if (StringUtils.isTrueStr(noHangUpFlag)) {
                clientManager.removeWatchClient(LongPollWatchClient.this);
                LogUtil.CLIENT_LOG.info("{}|{}|{}|{}|{}|{}|{}", System.currentTimeMillis() - start, "nohangup",
                        RequestUtil.getRemoteIp(req), "polling", getWatchKey().size(), probeRequestSize,
                        changedGroups.size());
                return;
            }
        }
        
        this.asyncTimeoutFuture = ConfigExecutor.scheduleLongPolling(() -> {
            try {
                clientManager.getRetainIps().put(getAddress(), System.currentTimeMillis());
                // Delete subscriber's relations.
                clientManager.removeWatchClient(LongPollWatchClient.this);
                if (isFixedPolling()) {
                    LogUtil.CLIENT_LOG.info("{}|{}|{}|{}|{}|{}", (System.currentTimeMillis() - createTime), "fix",
                            RequestUtil.getRemoteIp((HttpServletRequest) context.getRequest()), "polling",
                            getWatchKey().size(), probeRequestSize);
                    List<String> changedGroups = MD5Util.compareMd5((HttpServletRequest) context.getRequest(),
                            (HttpServletResponse) context.getResponse(), getWatchKey());
                    if (changedGroups.size() > 0) {
                        sendResponse(changedGroups);
                    } else {
                        sendResponse(null);
                    }
                } else {
                    LogUtil.CLIENT_LOG.info("{}|{}|{}|{}|{}|{}", (System.currentTimeMillis() - createTime), "timeout",
                            RequestUtil.getRemoteIp((HttpServletRequest) context.getRequest()), "polling",
                            getWatchKey().size(), probeRequestSize);
                    sendResponse(null);
                }
            } catch (Throwable t) {
                LogUtil.DEFAULT_LOG.error("long polling error:" + t.getMessage(), t.getCause());
            }
            
        }, timeoutTime, TimeUnit.MILLISECONDS);
    }
    
    private static boolean isFixedPolling() {
        return SwitchService.getSwitchBoolean(SwitchService.FIXED_POLLING, false);
    }
    
    private static int getFixedPollingInterval() {
        return SwitchService.getSwitchInteger(SwitchService.FIXED_POLLING_INTERVAL, FIXED_POLLING_INTERVAL_MS);
    }
    
    public int getProbeRequestSize() {
        return probeRequestSize;
    }
    
    @Override
    protected void notifyChangeEvent(LocalDataChangeEvent event) {
        LogUtil.CLIENT_LOG
                .info("{}|{}|{}|{}|{}|{}|{}", (System.currentTimeMillis() - event.getChangeTime()), "in-advance",
                        getAddress(), "polling", getWatchKey().size(), getProbeRequestSize(), event.groupKey);
        sendResponse(Collections.singletonList(event.groupKey));
    }
    
    void sendResponse(List<String> changedGroups) {
        // Cancel time out task.
        if (null != asyncTimeoutFuture) {
            asyncTimeoutFuture.cancel(false);
        }
        generateResponse(changedGroups);
    }
    
    void generateResponse(List<String> changedGroups) {
        if (null == changedGroups) {
            // Tell web container to send http response.
            context.complete();
            return;
        }
        HttpServletResponse response = (HttpServletResponse) context.getResponse();
        try {
            // Disable cache.
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-cache,no-store");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(MD5Util.compareMd5ResultString(changedGroups));
            context.complete();
        } catch (Exception ex) {
            LogUtil.PULL_LOG.error(ex.toString(), ex);
            context.complete();
        }
    }
    
    void generateResponse(HttpServletRequest request, HttpServletResponse response, List<String> changedGroups) {
        if (null == changedGroups) {
            return;
        }
        
        try {
            final String respString = MD5Util.compareMd5ResultString(changedGroups);
            // Disable cache.
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-cache,no-store");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(respString);
        } catch (Exception ex) {
            PULL_LOG.error(ex.toString(), ex);
        }
    }
    
    @Override
    protected String protocol() {
        return "HttpLongPoll";
    }
    
    public static LongPollWatchClientBuilder builder() {
        return new LongPollWatchClientBuilder();
    }
    
    public static final class LongPollWatchClientBuilder {
        
        private AsyncContext context;
        
        private long timeoutTime;
        
        private String tag;
        
        private String appName;
        
        private String address;
        
        private String namespace;
        
        private Map<String, String> watchKey;
        
        private LongPollWatchClientBuilder() {
        }
        
        public LongPollWatchClientBuilder context(AsyncContext context) {
            this.context = context;
            // AsyncContext.setTimeout() is incorrect, Control by oneself
            this.context.setTimeout(0L);
            return this;
        }
        
        public LongPollWatchClientBuilder timeoutTime(long timeoutTime) {
            this.timeoutTime = timeoutTime;
            return this;
        }
        
        public LongPollWatchClientBuilder tag(String tag) {
            this.tag = tag;
            return this;
        }
        
        public LongPollWatchClientBuilder appName(String appName) {
            this.appName = appName;
            return this;
        }
        
        public LongPollWatchClientBuilder address(String address) {
            this.address = address;
            return this;
        }
        
        public LongPollWatchClientBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }
        
        public LongPollWatchClientBuilder watchKey(Map<String, String> watchKey) {
            this.watchKey = watchKey;
            return this;
        }
        
        public LongPollWatchClient build() {
            LongPollWatchClient longPollWatchClient = new LongPollWatchClient(appName, address, namespace, watchKey,
                    timeoutTime, context);
            longPollWatchClient.setTag(tag);
            return longPollWatchClient;
        }
    }
}
