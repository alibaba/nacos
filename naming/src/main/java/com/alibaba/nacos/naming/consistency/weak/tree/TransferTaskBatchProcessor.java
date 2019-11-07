/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author satjd
 */
@Component
public class TransferTaskBatchProcessor implements Runnable{

    @Autowired
    private ProtocolConfig protocolConfig;

    @Autowired
    private DatumStoreService datumStoreService;

    @Autowired
    private TreePeerSet treePeerSet;

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);

            t.setDaemon(true);
            t.setName("com.alibaba.nacos.naming.tree.subscribemanager.taskprocesser.batch");

            return t;
        }
    });

    private final LinkedBlockingQueue<TransferTask> taskHolder = new LinkedBlockingQueue<>();

    @PostConstruct
    private void init() {
        scheduler.schedule(this,100L, TimeUnit.MILLISECONDS);
    }

    public void addTask(TransferTask task) {
        while (!taskHolder.offer(task)) {
            Loggers.TREE.warn("Retry add transfer task");
        }
    }

    private void sendDatum(Map<TreePeer,List<Datum>> sourceToDatum) throws Exception {
        for (Map.Entry<TreePeer,List<Datum>> entry : sourceToDatum.entrySet()) {
            JSONArray jsonArray = new JSONArray();
            jsonArray.addAll(entry.getValue());

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("datums",jsonArray);
            bodyJson.put("source",entry.getKey());

            String body = bodyJson.toJSONString();

            Set<TreePeer> curChild = treePeerSet.getCurrentChild(entry.getKey());

            for (TreePeer child : curChild) {
                String url = "http://" + child.ip
                    + UtilsAndCommons.IP_PORT_SPLITER
                    + child.port
                    + RunningConfig.getContextPath()
                    + ProtocolConfig.TREE_API_ON_PUB_PATH_BATCH;

                HttpClient.asyncHttpPostLarge(url, null, body, new AsyncCompletionHandler<Integer>() {
                    @Override
                    public Integer onCompleted(Response response) {
                        if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                            Loggers.TREE.warn("[TREE] failed to publish data to peer, http code={}",
                                 response.getStatusCode());
                            return 1;
                        }
                        return 0;
                    }

                    @Override
                    public STATE onContentWriteCompleted() {
                        return STATE.CONTINUE;
                    }
                });
            }
        }
    }

    @Override
    public void run() {
        LinkedList<TransferTask> retrieved = new LinkedList<>();
        long lastExecutedTs = 0;
        while (true) {
            TransferTask task = null;
            try {
                task = taskHolder.take();
            } catch (InterruptedException e) {
                Loggers.TREE.error(e.toString());
            }

            if (task == null) {
                continue;
            }

            retrieved.add(task);

            long now = System.currentTimeMillis();
            if (retrieved.size() >= protocolConfig.getTransferTaskBatchSize() ||
                now - lastExecutedTs >= protocolConfig.getTransferTaskInterval()) {
                lastExecutedTs = now;

                Map<TreePeer, List<Datum>> sourceToDatum = new HashMap<>(256);
                Map<String,TransferTask> taskMap = new HashMap<>(retrieved.size());
                for (TransferTask t : retrieved) {
                    // 按照key对task进行deduplicate
                    taskMap.put(t.datum.key,t);
                }

                for (TransferTask t : taskMap.values()) {
                    // 此处只保留key上的最新数据
                    Datum d = datumStoreService.getDatumCache().get(t.datum.key);
                    if (d == null) {
                        continue;
                    }

                    TreePeer source = t.source;
                    // 按照source进行归类，之后发送到不同的child
                    sourceToDatum.putIfAbsent(source,new LinkedList<>());

                    sourceToDatum.get(source).add(d);
                }

                try {
                    sendDatum(sourceToDatum);
                } catch (Exception e) {
                    Loggers.TREE.error(e.toString());
                } finally {
                    retrieved.clear();
                }
            }
        }
    }
}
