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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.LogUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author harold
 */
public class BeatReactor {

    private ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("com.alibaba.nacos.naming.beat.sender");
            return thread;
        }
    });

    private long clientBeatInterval = 10 * 1000;

    private NamingProxy serverProxy;

    public final Map<String, BeatInfo> dom2Beat = new ConcurrentHashMap<String, BeatInfo>();

    public BeatReactor(NamingProxy serverProxy) {
        this.serverProxy = serverProxy;
        executorService.scheduleAtFixedRate(new BeatProcessor(), 0, clientBeatInterval, TimeUnit.MILLISECONDS);
    }

    public void addBeatInfo(String dom, BeatInfo beatInfo) {
        LogUtils.LOG.info("BEAT", "adding service:" + dom + " to beat map.");
        dom2Beat.put(dom, beatInfo);
    }

    public void removeBeatInfo(String dom) {
        LogUtils.LOG.info("BEAT", "removing service:" + dom + " from beat map.");
        dom2Beat.remove(dom);
    }

    class BeatProcessor implements Runnable {

        @Override
        public void run() {
            try {
                for (Map.Entry<String, BeatInfo> entry : dom2Beat.entrySet()) {
                    BeatInfo beatInfo = entry.getValue();
                    executorService.schedule(new BeatTask(beatInfo), 0, TimeUnit.MILLISECONDS);
                    LogUtils.LOG.debug("BEAT", "send beat to server: ", beatInfo.toString());
                }
            } catch (Exception e) {
                LogUtils.LOG.error("CLIENT-BEAT", "Exception while scheduling beat.", e);
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
            Map<String, String> params = new HashMap<String, String>(2);
            params.put("beat", JSON.toJSONString(beatInfo));
            params.put("dom", beatInfo.getDom());

            try {
                String result = serverProxy.callAllServers(UtilAndComs.NACOS_URL_BASE + "/api/clientBeat", params);
                JSONObject jsonObject = JSON.parseObject(result);

                if (jsonObject != null) {
                    clientBeatInterval = jsonObject.getLong("clientBeatInterval");

                }
            } catch (Exception e) {
                LogUtils.LOG.error("CLIENT-BEAT", "failed to send beat: " + JSON.toJSONString(beatInfo), e);
            }
        }
    }
}
