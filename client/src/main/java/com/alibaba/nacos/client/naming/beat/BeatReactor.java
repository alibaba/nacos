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
package com.alibaba.nacos.client.naming.beat;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;

import java.util.Map;
import java.util.concurrent.*;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * @author harold
 */
public class BeatReactor {

    private ScheduledExecutorService executorService;

    private volatile long clientBeatInterval = 5 * 1000;

    private NamingProxy serverProxy;

    public final Map<String, BeatInfo> dom2Beat = new ConcurrentHashMap<String, BeatInfo>();

    public BeatReactor(NamingProxy serverProxy) {
        this(serverProxy, UtilAndComs.DEFAULT_CLIENT_BEAT_THREAD_COUNT);
    }

    public BeatReactor(NamingProxy serverProxy, int threadCount) {
        this.serverProxy = serverProxy;

        executorService = new ScheduledThreadPoolExecutor(threadCount, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("com.alibaba.nacos.naming.beat.sender");
                return thread;
            }
        });

        executorService.schedule(new BeatProcessor(), 0, TimeUnit.MILLISECONDS);
    }

    public void addBeatInfo(String dom, BeatInfo beatInfo) {
        NAMING_LOGGER.info("[BEAT] adding beat: {} to beat map.", beatInfo);
        dom2Beat.put(buildKey(dom, beatInfo.getIp(), beatInfo.getPort()), beatInfo);
        MetricsMonitor.getDom2BeatSizeMonitor().set(dom2Beat.size());
    }

    public void removeBeatInfo(String dom, String ip, int port) {
        NAMING_LOGGER.info("[BEAT] removing beat: {}:{}:{} from beat map.", dom, ip, port);
        dom2Beat.remove(buildKey(dom, ip, port));
        MetricsMonitor.getDom2BeatSizeMonitor().set(dom2Beat.size());
    }

    public String buildKey(String dom, String ip, int port) {
        return dom + Constants.NAMING_INSTANCE_ID_SPLITTER + ip + Constants.NAMING_INSTANCE_ID_SPLITTER + port;
    }

    class BeatProcessor implements Runnable {

        @Override
        public void run() {
            try {
                for (Map.Entry<String, BeatInfo> entry : dom2Beat.entrySet()) {
                    BeatInfo beatInfo = entry.getValue();
                    if (beatInfo.isScheduled()) {
                        continue;
                    }
                    beatInfo.setScheduled(true);
                    executorService.schedule(new BeatTask(beatInfo), 0, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                NAMING_LOGGER.error("[CLIENT-BEAT] Exception while scheduling beat.", e);
            } finally {
                executorService.schedule(this, clientBeatInterval, TimeUnit.MILLISECONDS);
            }
        }
    }

    class BeatTask implements Runnable {

        BeatInfo beatInfo;

        public BeatTask(BeatInfo beatInfo) {
            this.beatInfo = beatInfo;
        }

        @Override
        public void run() {
            long result = serverProxy.sendBeat(beatInfo);
            beatInfo.setScheduled(false);
            if (result > 0) {
                clientBeatInterval = result;
            }
        }
    }
}
