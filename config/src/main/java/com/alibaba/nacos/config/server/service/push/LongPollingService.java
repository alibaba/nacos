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

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.service.ConfigService;
import com.alibaba.nacos.config.server.service.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.Protocol;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher.AbstractEventListener;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher.Event;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.nacos.config.server.utils.LogUtil.memoryLog;
import static com.alibaba.nacos.config.server.utils.LogUtil.pullLog;

/**
 * 长轮询服务。负责处理
 *
 * @author Nacos
 * @author liaochuntao
 */
@Service
public class LongPollingService extends AbstractEventListener {

    private static final int START_LONG_POLLING_VERSION_NUM = 204;

    private static final int FIXED_POLLING_INTERVAL_MS = 10000;

    private static final int SAMPLE_PERIOD = 100;

    private static final int SAMPLE_TIMES = 3;

    static final String LONG_POLLING_HEADER = "Long-Pulling-Timeout";

    static final String LONG_POLLING_NO_HANG_UP_HEADER = "Long-Pulling-Timeout-No-Hangup";

    private final ScheduledExecutorService longPullScheduler;

    private final LongPollClientManager clientManager;

    private final AtomicInteger id = new AtomicInteger(0);

    private Map<String, Long> retainIps = new ConcurrentHashMap<String, Long>();

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public LongPollingService(LongPollClientManager clientManager) {
        this.clientManager = clientManager;
        longPullScheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() / 2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("com.alibaba.nacos.LongPolling-" + id.getAndIncrement());
            return t;
        });
        longPullScheduler.scheduleWithFixedDelay(new StatTask(), 0L, 10L, TimeUnit.SECONDS);
    }

    private static boolean isFixedPolling() {
        return SwitchService.getSwitchBoolean(SwitchService.FIXED_POLLING, false);
    }

    private static boolean isSupportLongPolling(HttpServletRequest req) {
        return Objects.nonNull(req.getHeader(LONG_POLLING_HEADER));
    }

    private static int getFixedPollingInterval() {
        return SwitchService.getSwitchInteger(SwitchService.FIXED_POLLING_INTERVAL, FIXED_POLLING_INTERVAL_MS);
    }

    public boolean isClientLongPolling(String clientIp) {
        return getClientPollingRecord(clientIp) != null;
    }

    public Map<String, String> getClientSubConfigInfo(String clientIp) {
        LongPollClientManager.WatchClient record = getClientPollingRecord(clientIp);
        if (record == null) {
            return Collections.<String, String>emptyMap();
        }
        return record.getClientMd5Map();
    }

    public SampleResult getSubscribleInfo(String dataId, String group, String tenant) {
        String groupKey = GroupKey.getKeyTenant(dataId, group, tenant);
        SampleResult sampleResult = new SampleResult();
        Map<String, String> lisentersGroupkeyStatus = clientManager.queryWatchClientByGroupKey(groupKey)
            .stream()
            .collect(HashMap::new, (m, e) -> m.put(e.getClientIp(), e.getClientMd5Map().get(groupKey)), HashMap::putAll);
        sampleResult.setLisentersGroupkeyStatus(lisentersGroupkeyStatus);
        return sampleResult;
    }

    public SampleResult getSubscribleInfoByIp(String clientIp) {
        SampleResult sampleResult = new SampleResult();
        Map<String, String> lisentersGroupkeyStatus = clientManager.allWatchClient().stream()
            .filter(watchClient -> Objects.equals(clientIp, watchClient.getClientIp()))
            .collect(HashMap::new, (m, e) -> m.putAll(e.getClientMd5Map()), HashMap::putAll);
        sampleResult.setLisentersGroupkeyStatus(lisentersGroupkeyStatus);
        return sampleResult;
    }

    /**
     * 聚合采样结果中的采样ip和监听配置的信息；合并策略用后面的覆盖前面的是没有问题的
     *
     * @param sampleResults sample Results
     * @return Results
     */
    public SampleResult mergeSampleResult(List<SampleResult> sampleResults) {
        SampleResult mergeResult = new SampleResult();
        Map<String, String> lisentersGroupkeyStatus = sampleResults.stream()
            .map(SampleResult::getLisentersGroupkeyStatus)
            .flatMap(stringMap -> stringMap.entrySet().stream())
            .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
        mergeResult.setLisentersGroupkeyStatus(lisentersGroupkeyStatus);
        return mergeResult;
    }

    public Map<String, Set<String>> collectApplicationSubscribeConfigInfos() {
        if (clientManager.allWatchClient() == null || clientManager.allWatchClient().isEmpty()) {
            return null;
        }
        final Map<String, Set<String>> app2Groupkeys = new HashMap<String, Set<String>>(50);
        clientManager.allWatchClient().stream()
            .filter(watchClient -> !(StringUtils.isEmpty(watchClient.getAppName()) || "unknown".equalsIgnoreCase(
                watchClient.getAppName())))
            .forEach(watchClient -> {
                Set<String> appSubscribeConfigs = app2Groupkeys.get(watchClient.getAppName());
                Set<String> clientSubscribeConfigs = watchClient.getClientMd5Map().keySet();
                if (appSubscribeConfigs == null) {
                    appSubscribeConfigs = new HashSet<String>(clientSubscribeConfigs.size());
                }
                appSubscribeConfigs.addAll(clientSubscribeConfigs);
                app2Groupkeys.put(watchClient.getAppName(), appSubscribeConfigs);
            });
        return app2Groupkeys;
    }

    public SampleResult getCollectSubscribleInfo(String dataId, String group, String tenant) {
        List<SampleResult> sampleResultLst = new ArrayList<SampleResult>(50);
        for (int i = 0; i < SAMPLE_TIMES; i++) {
            SampleResult sampleTmp = getSubscribleInfo(dataId, group, tenant);
            if (sampleTmp != null) {
                sampleResultLst.add(sampleTmp);
            }
            if (i < SAMPLE_TIMES - 1) {
                try {
                    Thread.sleep(SAMPLE_PERIOD);
                } catch (InterruptedException e) {
                    LogUtil.clientLog.error("sleep wrong", e);
                }
            }
        }

        SampleResult sampleResult = mergeSampleResult(sampleResultLst);
        return sampleResult;
    }

    public SampleResult getCollectSubscribleInfoByIp(String ip) {
        SampleResult sampleResult = new SampleResult();
        sampleResult.setLisentersGroupkeyStatus(new HashMap<String, String>(50));
        for (int i = 0; i < SAMPLE_TIMES; i++) {
            SampleResult sampleTmp = getSubscribleInfoByIp(ip);
            if (sampleTmp != null) {
                if (sampleTmp.getLisentersGroupkeyStatus() != null
                    && !sampleResult.getLisentersGroupkeyStatus().equals(sampleTmp.getLisentersGroupkeyStatus())) {
                    sampleResult.getLisentersGroupkeyStatus().putAll(sampleTmp.getLisentersGroupkeyStatus());
                }
            }
            if (i < SAMPLE_TIMES - 1) {
                try {
                    Thread.sleep(SAMPLE_PERIOD);
                } catch (InterruptedException e) {
                    LogUtil.clientLog.error("sleep wrong", e);
                }
            }
        }
        return sampleResult;
    }

    private LongPollClientManager.WatchClient getClientPollingRecord(String clientIp) {
        if (clientManager.allWatchClient() == null) {
            return null;
        }
        for (LongPollClientManager.WatchClient watchClient : clientManager.allWatchClient()) {
            HttpServletRequest request = (HttpServletRequest) watchClient.getContext().getRequest();
            if (clientIp.equals(RequestUtil.getRemoteIp(request))) {
                return watchClient;
            }
        }
        return null;
    }

    public void createWatch(HttpServletRequest request, HttpServletResponse rsp, Map<String, String> clientMd5Map,
                            int probeRequestSize) throws IOException {
        LongPollClientManager.WatchClient watchClient =
            LongPollClientManager.WatchClient.buildWatchClientFromRequest(clientManager, request.startAsync(), request);
        watchClient.setClientMd5Map(clientMd5Map);
        watchClient.setListenCnt(clientMd5Map.size());
        watchClient.setProbeRequestSize(probeRequestSize);
        clientManager.createWatchClient(watchClient, clientMd5Map);
        if (isSupportLongPolling(request)) {
            watchClient.startWatch();
        } else {
            doShortListen(watchClient);
        }
    }

    private void doShortListen(LongPollClientManager.WatchClient watchClient) throws IOException {
        HttpServletRequest request = (HttpServletRequest) watchClient.getContext().getRequest();
        HttpServletResponse response = (HttpServletResponse) watchClient.getContext().getResponse();
        List<String> changedGroups = MD5Util.compareMd5(watchClient.getTag(), watchClient.getClientIp(), watchClient.getClientMd5Map());
        String oldResult = MD5Util.compareMd5OldResult(changedGroups);
        String newResult = MD5Util.compareMd5ResultString(changedGroups);

        String version = request.getHeader(Constants.CLIENT_VERSION_HEADER);
        if (version == null) {
            version = "2.0.0";
        }
        int versionNum = Protocol.getVersionNumber(version);

        // Before 2.0.4 version, the return value in the header
        if (versionNum < START_LONG_POLLING_VERSION_NUM) {
            response.addHeader(Constants.PROBE_MODIFY_RESPONSE, oldResult);
            response.addHeader(Constants.PROBE_MODIFY_RESPONSE_NEW, newResult);
        } else {
            request.setAttribute("content", newResult);
        }

        // Disable caching
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setHeader("Cache-Control", "no-cache,no-store");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    public List<Class<? extends Event>> interest() {
        List<Class<? extends Event>> eventTypes = new ArrayList<Class<? extends Event>>();
        eventTypes.add(LocalDataChangeEvent.class);
        return eventTypes;
    }

    @Override
    public void onEvent(Event event) {
        if (isFixedPolling()) {
            // ignore
        } else {
            if (event instanceof LocalDataChangeEvent) {
                LocalDataChangeEvent evt = (LocalDataChangeEvent) event;
                longPullScheduler.execute(new NotifyWatchClient(evt.groupKey, evt.isBeta, evt.betaIps));
            }
        }
    }

    class NotifyWatchClient implements Runnable {
        @Override
        public void run() {
            AtomicInteger cnt = new AtomicInteger(0);
            try {
                ConfigService.getContentBetaMd5(groupKey);
                long finishWorkCnt = clientManager.queryWatchClientByGroupKey(groupKey)
                    .stream()
                    .filter(this::canNotify)
                    .peek(watchClient -> {
                        getRetainIps().put(watchClient.getClientIp(), System.currentTimeMillis());
                        watchClient.writeResponse(Collections.singletonList(groupKey));
                        cnt.incrementAndGet();
                        LogUtil.clientLog.info("{}|{}|{}|{}|{}|{}|{}",
                            (System.currentTimeMillis() - changeTime),
                            "in-advance",
                            watchClient.getClientIp(),
                            "polling",
                            watchClient.getListenCnt(), watchClient.getProbeRequestSize(), groupKey);
                    })
                    .count();
                LogUtil.publishLog.info("[NotifyWatchClient] finish publish work : {}, change groupKey is : {}, " +
                    "occurrence time is : {}", cnt.get(), groupKey, changeTime);
            } catch (Exception e) {
                LogUtil.publishLog.error("[NotifyWatchClient] err : {}", e.getMessage());
            }
        }

        private boolean canNotify(LongPollClientManager.WatchClient watchClient) {
            if (isBeta && !betaIps.contains(watchClient.getClientIp())) {
                return false;
            }
            return !StringUtils.isNotBlank(tag) || tag.equals(watchClient.getTag());
        }

        NotifyWatchClient(String groupKey) {
            this(groupKey, false, null);
        }

        NotifyWatchClient(String groupKey, boolean isBeta, List<String> betaIps) {
            this(groupKey, isBeta, betaIps, null);
        }

        NotifyWatchClient(String groupKey, boolean isBeta, List<String> betaIps, String tag) {
            this.groupKey = groupKey;
            this.isBeta = isBeta;
            this.betaIps = betaIps;
            this.tag = tag;
        }

        final String groupKey;
        final long changeTime = System.currentTimeMillis();
        final boolean isBeta;
        final List<String> betaIps;
        final String tag;
    }

    class StatTask implements Runnable {
        @Override
        public void run() {
            memoryLog.info("[long-pulling] client count " + clientManager.allWatchClient().size());
            MetricsMonitor.getLongPollingMonitor().set(clientManager.allWatchClient().size());
        }
    }

    public Map<String, Long> getRetainIps() {
        return retainIps;
    }

    public void setRetainIps(Map<String, Long> retainIps) {
        this.retainIps = retainIps;
    }

}
