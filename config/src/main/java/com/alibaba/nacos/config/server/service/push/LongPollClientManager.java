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

package com.alibaba.nacos.config.server.service.push;

import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import org.springframework.stereotype.Component;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.alibaba.nacos.config.server.utils.LogUtil.pullLog;

/**
 * @author liaochuntao
 * @date 2019-08-28 10:26
 **/
@Component(value = "longPollClientManager")
public class LongPollClientManager {

    private static final int FIXED_POLLING_INTERVAL_MS = 10000;

    private ConcurrentHashMap<WatchKey, Map<String, WatchClient>> watchClientManager = new ConcurrentHashMap<>(32);

    public void createWatchClient(WatchClient watchClient, Map<String, String> clientMd5Map) {
        clientMd5Map.forEach((key1, value) -> {
            WatchKey watchKey = WatchKey.newInstance();
            WatchClient client = watchClient.clone();

            watchKey.groupKey = key1;
            client.md5 = value;

            watchClientManager
                .computeIfAbsent(watchKey, key -> new ConcurrentHashMap<>(6))
                .put(client.clientIp, client);
        });
    }

    public List<LongPollClientManager.WatchClient> allWatchClient() {
        return watchClientManager.values()
            .stream()
            .flatMap(stringWatchClientMap -> stringWatchClientMap.values().stream())
            .collect(Collectors.toList());
    }

    public List<LongPollClientManager.WatchClient> queryWatchClientByGroupKey(String groupKey) {
        return watchClientManager.entrySet()
            .stream()
            .filter(watchKeyMapEntry -> Objects.equals(watchKeyMapEntry.getKey().groupKey, groupKey))
            .map(Map.Entry::getValue)
            .flatMap(watchClientObjectMap -> watchClientObjectMap.values().stream())
            .collect(Collectors.toList());
    }

    public void removeWatchClient(List<WatchClient> watchClients) {
        watchClientManager.values().forEach(watchClientObjectMap -> {
            watchClients.forEach(watchClient -> watchClientObjectMap.remove(watchClient.clientIp));
        });
    }

    private static boolean isFixedPolling() {
        return SwitchService.getSwitchBoolean(SwitchService.FIXED_POLLING, false);
    }

    private static int getFixedPollingInterval() {
        return SwitchService.getSwitchInteger(SwitchService.FIXED_POLLING_INTERVAL, FIXED_POLLING_INTERVAL_MS);
    }

    static class WatchKey {

        private String groupKey;

        private WatchKey() {}

        public static WatchKey newInstance() {
            return new WatchKey();
        }

        public String getGroupKey() {
            return groupKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            WatchKey watchKey = (WatchKey) o;
            return Objects.equals(getGroupKey(), watchKey.getGroupKey());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getGroupKey());
        }
    }

    static class WatchClient implements Cloneable {

        private LongPollClientManager manager;
        private AsyncContext context;
        private String clientIp;
        private String md5;
        private long createTime;
        private String appName;
        private String tag;
        private long timeout;
        private int listenCnt;
        private int probeRequestSize;
        private String noHangUpFlag;
        private Map<String, String> clientMd5Map;

        private WatchClient() {}

        public static WatchClient newInstance() {
            return new WatchClient();
        }

        public static WatchClient buildWatchClientFromRequest(LongPollClientManager manager, AsyncContext context, HttpServletRequest request) {
            WatchClient client = WatchClient.newInstance();
            client.manager = manager;
            client.context = context;
            client.createTime = System.currentTimeMillis();
            client.clientIp = RequestUtil.getRemoteIp(request);
            client.appName = request.getHeader(RequestUtil.CLIENT_APPNAME_HEADER);
            client.tag = request.getHeader("Vipserver-Tag");
            client.timeout = Long.parseLong(request.getHeader(LongPollingService.LONG_POLLING_HEADER));
            client.noHangUpFlag = request.getHeader(LongPollingService.LONG_POLLING_NO_HANG_UP_HEADER);
            return client;
        }

        void startWatch() {

            int delayTime = SwitchService.getSwitchInteger(SwitchService.FIXED_DELAY_TIME, 500);
            // 500 ms early return to response
            this.timeout = Math.max(10000, getTimeout() - delayTime);

            if (!isFixedPolling()) {
                shortListen();
                return;
            } else {
                this.timeout = Math.max(10000, getFixedPollingInterval());
            }

            this.context.setTimeout(timeout);
            this.context.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    // When the task is complete, remove self
                    WatchClient.this.manager.removeWatchClient(Collections.singletonList(WatchClient.this));
                }

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    // When a timeout occurs, check whether there is a configuration update again,
                    // end the asynchronous servlets, remove self
                    if (!isFixedPolling()) {
                        LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}",
                            (System.currentTimeMillis() - createTime),
                            "fix", clientIp,
                            "polling",
                            clientMd5Map.size(), probeRequestSize);
                        List<String> changedGroups = MD5Util.compareMd5(tag, clientIp, clientMd5Map);
                        if (changedGroups.isEmpty()) {
                            context.complete();
                        } else {
                            writeResponse(MD5Util.compareMd5ResultString(changedGroups));
                        }
                    } else {
                        LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}",
                            (System.currentTimeMillis() - createTime),
                            "timeout", WatchClient.this.clientIp,
                            "polling",
                            clientMd5Map.size(), probeRequestSize);
                        WatchClient.this.context.complete();
                    }

                    WatchClient.this.manager.removeWatchClient(Collections.singletonList(WatchClient.this));
                }

                @Override
                public void onError(AsyncEvent event) throws IOException {
                    // When a error occurs, end the asynchronous servlets, remove self
                    pullLog.error(event.toString(), event.getThrowable());
                    WatchClient.this.context.complete();
                }

                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {}
            });
        }

        void shortListen() {
            long start = System.currentTimeMillis();
            List<String> changedGroups = MD5Util.compareMd5(tag, clientIp, clientMd5Map);
            if (!changedGroups.isEmpty()) {
                try {
                    String respString = MD5Util.compareMd5ResultString(changedGroups);
                    writeResponse(respString);
                    LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}|{}",
                        System.currentTimeMillis() - start, "instant", clientIp, "polling",
                        listenCnt, probeRequestSize, changedGroups.size());
                } catch (IOException e) {
                    LogUtil.clientLog.error(e.toString(), e);
                    context.complete();
                }
            } else if (noHangUpFlag != null && noHangUpFlag.equalsIgnoreCase(Boolean.TRUE.toString())) {
                LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}|{}", System.currentTimeMillis() - start, "nohangup",
                    clientIp, "polling", listenCnt, probeRequestSize,
                    changedGroups.size());
            }
        }

        public LongPollClientManager getManager() {
            return manager;
        }

        public void setManager(LongPollClientManager manager) {
            this.manager = manager;
        }

        public void setContext(AsyncContext context) {
            this.context = context;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }

        public void setMd5(String md5) {
            this.md5 = md5;
        }

        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        public void setListenCnt(int listenCnt) {
            this.listenCnt = listenCnt;
        }

        public void setProbeRequestSize(int probeRequestSize) {
            this.probeRequestSize = probeRequestSize;
        }

        public void setClientMd5Map(Map<String, String> clientMd5Map) {
            this.clientMd5Map = clientMd5Map;
        }

        public AsyncContext getContext() {
            return context;
        }

        public String getClientIp() {
            return clientIp;
        }

        public String getMd5() {
            return md5;
        }

        public long getCreateTime() {
            return createTime;
        }

        public String getAppName() {
            return appName;
        }

        public String getTag() {
            return tag;
        }

        public long getTimeout() {
            return timeout;
        }

        public int getListenCnt() {
            return listenCnt;
        }

        public int getProbeRequestSize() {
            return probeRequestSize;
        }

        public Map<String, String> getClientMd5Map() {
            return clientMd5Map;
        }

        public void writeResponse(String body) {
            HttpServletResponse response = (HttpServletResponse) context.getResponse();
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setHeader("Cache-Control", "no-cache,no-store");
            response.setStatus(HttpServletResponse.SC_OK);
            try {
                response.getWriter().println(body);
                context.complete();
            } catch (Exception e) {
                pullLog.error(e.toString(), e);
                context.complete();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            WatchClient that = (WatchClient) o;
            return Objects.equals(clientIp, that.clientIp) && Objects.equals(md5, that.md5);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clientIp, md5);
        }

        protected WatchClient clone() {
            WatchClient cloneClient = WatchClient.newInstance();
            cloneClient.manager = this.manager;
            cloneClient.createTime = this.createTime;
            cloneClient.context = this.context;
            cloneClient.tag = this.tag;
            cloneClient.appName = this.appName;
            cloneClient.clientIp = this.clientIp;
            cloneClient.md5 = this.md5;
            cloneClient.timeout = this.timeout;
            cloneClient.listenCnt = this.listenCnt;
            cloneClient.probeRequestSize = this.probeRequestSize;
            cloneClient.noHangUpFlag = this.noHangUpFlag;
            return cloneClient;
        }
    }

}
