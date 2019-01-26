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
package com.alibaba.nacos.naming.raft;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPOutputStream;

import static com.alibaba.nacos.common.util.SystemUtils.STANDALONE_MODE;

/**
 * @author nacos
 */
public class RaftCore {

    public static final String API_VOTE = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft/vote";

    public static final String API_BEAT = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft/beat";

    public static final String API_PUB = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft/publish";

    public static final String API_UNSF_PUB = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft/unSafePublish";

    public static final String API_DEL = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft/delete";

    public static final String API_GET = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft/get";

    public static final String API_ON_PUB = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft/onPublish";

    public static final String API_ON_DEL = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft/onDelete";

    public static final String API_GET_PEER = UtilsAndCommons.NACOS_NAMING_CONTEXT + "/raft/getPeer";

    private static ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);

            t.setDaemon(true);
            t.setName("com.alibaba.nacos.naming.raft.notifier");

            return t;
        }
    });

    public static final Lock OPERATE_LOCK = new ReentrantLock();

    public static final int PUBLISH_TERM_INCREASE_COUNT = 100;

    private static final int INIT_LOCK_TIME_SECONDS = 3;

    private static volatile boolean initialized = false;

    private static Lock lock = new ReentrantLock();

    private static volatile List<RaftListener> listeners = new CopyOnWriteArrayList<>();

    private static volatile ConcurrentMap<String, Datum> datums = new ConcurrentHashMap<String, Datum>();

    private static PeerSet peers = new PeerSet();

    public static volatile Notifier notifier = new Notifier();

    public static void init() throws Exception {

        Loggers.RAFT.info("initializing Raft sub-system");

        executor.submit(notifier);

        peers.add(NamingProxy.getServers());

        long start = System.currentTimeMillis();

        RaftStore.load();

        Loggers.RAFT.info("cache loaded, peer count: {}, datum count: {}, current term: {}",
            peers.size(), datums.size(), peers.getTerm());

        while (true) {
            if (notifier.tasks.size() <= 0) {
                break;
            }
            Thread.sleep(1000L);
            System.out.println(notifier.tasks.size());
        }

        Loggers.RAFT.info("finish to load data from disk, cost: {} ms.", (System.currentTimeMillis() - start));

        GlobalExecutor.register(new MasterElection());
        GlobalExecutor.register1(new HeartBeat());
        GlobalExecutor.register(new AddressServerUpdater(), GlobalExecutor.ADDRESS_SERVER_UPDATE_INTERVAL_MS);

        if (peers.size() > 0) {
            if (lock.tryLock(INIT_LOCK_TIME_SECONDS, TimeUnit.SECONDS)) {
                initialized = true;
                lock.unlock();
            }
        } else {
            throw new Exception("peers is empty.");
        }

        Loggers.RAFT.info("timer started: leader timeout ms: {}, heart-beat timeout ms: {}",
            GlobalExecutor.LEADER_TIMEOUT_MS, GlobalExecutor.HEARTBEAT_INTERVAL_MS);
    }

    public static List<RaftListener> getListeners() {
        return listeners;
    }


    public static void signalPublish(String key, String value) throws Exception {

        long start = System.currentTimeMillis();
        final Datum datum = new Datum();
        datum.key = key;
        datum.value = value;

        if (RaftCore.getDatum(key) == null) {
            datum.timestamp.set(1L);
        } else {
            datum.timestamp.set(RaftCore.getDatum(key).timestamp.incrementAndGet());
        }

        JSONObject json = new JSONObject();
        json.put("datum", datum);
        json.put("source", peers.local());
        json.put("increaseTerm", false);

        onPublish(datum, peers.local(), false);

        final String content = JSON.toJSONString(json);

        for (final String server : peers.allServersIncludeMyself()) {
            if (isLeader(server)) {
                continue;
            }
            final String url = buildURL(server, API_ON_PUB);
            HttpClient.asyncHttpPostLarge(url, Arrays.asList("key=" + key), content, new AsyncCompletionHandler<Integer>() {
                @Override
                public Integer onCompleted(Response response) throws Exception {
                    if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                        Loggers.RAFT.warn("[RAFT] failed to publish data to peer, datumId={}, peer={}, http code={}", datum.key, server, response.getStatusCode());
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

        long end = System.currentTimeMillis();
        Loggers.RAFT.info("signalPublish cost {} ms, key: {}", (end - start), key);

    }

    public static void doSignalPublish(String key, String value, boolean locked) throws Exception {
        if (!RaftCore.isLeader()) {
            JSONObject params = new JSONObject();
            params.put("key", key);
            params.put("value", value);
            params.put("locked", locked);
            Map<String, String> parameters = new HashMap<>(1);
            parameters.put("key", key);

            RaftProxy.proxyPostLarge(API_PUB, params.toJSONString(), parameters);

            return;
        }

        if (!RaftCore.isLeader()) {
            throw new IllegalStateException("I'm not leader, can not handle update/delete operation");
        }

        if (locked) {
            signalPublishLocked(key, value);
        } else {
            signalPublish(key, value);
        }
    }

    public static void signalPublishLocked(String key, String value) throws Exception {

        try {
            RaftCore.OPERATE_LOCK.lock();
            long start = System.currentTimeMillis();
            final Datum datum = new Datum();
            datum.key = key;
            datum.value = value;
            if (RaftCore.getDatum(key) == null) {
                datum.timestamp.set(1L);
            } else {
                datum.timestamp.set(RaftCore.getDatum(key).timestamp.incrementAndGet());
            }

            JSONObject json = new JSONObject();
            json.put("datum", datum);
            json.put("source", peers.local());
            json.put("increaseTerm", true);

            onPublish(datum, peers.local(), true);

            final String content = JSON.toJSONString(json);

            final CountDownLatch latch = new CountDownLatch(peers.majorityCount());
            for (final String server : peers.allServersIncludeMyself()) {
                if (isLeader(server)) {
                    latch.countDown();
                    continue;
                }
                final String url = buildURL(server, API_ON_PUB);
                HttpClient.asyncHttpPostLarge(url, Arrays.asList("key=" + key), content, new AsyncCompletionHandler<Integer>() {
                    @Override
                    public Integer onCompleted(Response response) throws Exception {
                        if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                            Loggers.RAFT.warn("[RAFT] failed to publish data to peer, datumId={}, peer={}, http code={}",
                                datum.key, server, response.getStatusCode());
                            return 1;
                        }
                        latch.countDown();
                        return 0;
                    }

                    @Override
                    public STATE onContentWriteCompleted() {
                        return STATE.CONTINUE;
                    }
                });

            }

            if (!latch.await(UtilsAndCommons.RAFT_PUBLISH_TIMEOUT, TimeUnit.MILLISECONDS)) {
                // only majority servers return success can we consider this update success
                Loggers.RAFT.info("data publish failed, caused failed to notify majority, key={}", key);
                throw new IllegalStateException("data publish failed, caused failed to notify majority, key=" + key);
            }

            long end = System.currentTimeMillis();
            Loggers.RAFT.info("signalPublish cost {} ms, key: {}", (end - start), key);
        } finally {
            RaftCore.OPERATE_LOCK.unlock();
        }
    }

    public static void signalDelete(final String key) throws Exception {

        OPERATE_LOCK.lock();
        try {

            if (!isLeader()) {
                Map<String, String> params = new HashMap<>(1);
                params.put("key", URLEncoder.encode(key, "UTF-8"));

                RaftProxy.proxyGET(API_DEL, params);
                return;
            }

            if (!RaftCore.isLeader()) {
                throw new IllegalStateException("I'm not leader, can not handle update/delete operation");
            }

            JSONObject json = new JSONObject();
            json.put("key", key);
            json.put("source", peers.local());

            for (final String server : peers.allServersIncludeMyself()) {
                String url = buildURL(server, API_ON_DEL);
                HttpClient.asyncHttpPostLarge(url, null, JSON.toJSONString(json)
                    , new AsyncCompletionHandler<Integer>() {
                        @Override
                        public Integer onCompleted(Response response) throws Exception {
                            if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                                Loggers.RAFT.warn("[RAFT] failed to delete data from peer, datumId={}, peer={}, http code={}", key, server, response.getStatusCode());
                                return 1;
                            }

                            RaftPeer local = peers.local();

                            local.resetLeaderDue();

                            return 0;
                        }
                    });
            }
        } finally {
            OPERATE_LOCK.unlock();
        }
    }

    public static void onPublish(JSONObject json, boolean increaseTerm) throws Exception {
        Datum datum = JSON.parseObject(json.getString("datum"), Datum.class);
        RaftPeer source = JSON.parseObject(json.getString("source"), RaftPeer.class);
        onPublish(datum, source, increaseTerm);
    }

    public static void onPublish(Datum datum, RaftPeer source, boolean increaseTerm) throws Exception {
        RaftPeer local = peers.local();
        if (StringUtils.isBlank(datum.value)) {
            Loggers.RAFT.warn("received empty datum");
            throw new IllegalStateException("received empty datum");
        }

        if (!peers.isLeader(source.ip)) {
            Loggers.RAFT.warn("peer {} tried to publish data but wasn't leader, leader: {}",
                JSON.toJSONString(source), JSON.toJSONString(getLeader()));
            throw new IllegalStateException("peer(" + source.ip + ") tried to publish " +
                "data but wasn't leader");
        }

        if (source.term.get() < local.term.get()) {
            Loggers.RAFT.warn("out of date publish, pub-term: {}, cur-term: {}",
                JSON.toJSONString(source), JSON.toJSONString(local));
            throw new IllegalStateException("out of date publish, pub-term:"
                + source.term.get() + ", cur-term: " + local.term.get());
        }

        local.resetLeaderDue();

        // do apply
        if (datum.key.startsWith(UtilsAndCommons.DOMAINS_DATA_ID_PRE) || UtilsAndCommons.INSTANCE_LIST_PERSISTED) {
            RaftStore.write(datum);
        }

        RaftCore.datums.put(datum.key, datum);

        if (increaseTerm) {
            if (isLeader()) {
                local.term.addAndGet(PUBLISH_TERM_INCREASE_COUNT);
            } else {
                if (local.term.get() + PUBLISH_TERM_INCREASE_COUNT > source.term.get()) {
                    //set leader term:
                    getLeader().term.set(source.term.get());
                    local.term.set(getLeader().term.get());
                } else {
                    local.term.addAndGet(PUBLISH_TERM_INCREASE_COUNT);
                }
            }
            RaftStore.updateTerm(local.term.get());
        }

        notifier.addTask(datum, Notifier.ApplyAction.CHANGE);

        Loggers.RAFT.info("data added/updated, key={}, term={}", datum.key, local.term);
    }

    public static void onDelete(JSONObject params) throws Exception {

        RaftPeer source = new RaftPeer();
        source.ip = params.getJSONObject("source").getString("ip");
        source.state = RaftPeer.State.valueOf(params.getJSONObject("source").getString("state"));
        source.term.set(params.getJSONObject("source").getLongValue("term"));
        source.heartbeatDueMs = params.getJSONObject("source").getLongValue("heartbeatDueMs");
        source.leaderDueMs = params.getJSONObject("source").getLongValue("leaderDueMs");
        source.voteFor = params.getJSONObject("source").getString("voteFor");

        RaftPeer local = peers.local();

        if (!peers.isLeader(source.ip)) {
            Loggers.RAFT.warn("peer {} tried to publish data but wasn't leader, leader: {}",
                JSON.toJSONString(source), JSON.toJSONString(getLeader()));
            throw new IllegalStateException("peer(" + source.ip + ") tried to publish data but wasn't leader");
        }

        if (source.term.get() < local.term.get()) {
            Loggers.RAFT.warn("out of date publish, pub-term: {}, cur-term: {}",
                JSON.toJSONString(source), JSON.toJSONString(local));
            throw new IllegalStateException("out of date publish, pub-term:"
                + source.term + ", cur-term: " + local.term);
        }

        local.resetLeaderDue();

        // do apply
        String key = params.getString("key");
        deleteDatum(key);

        if (key.startsWith(UtilsAndCommons.DOMAINS_DATA_ID_PRE)) {

            if (local.term.get() + PUBLISH_TERM_INCREASE_COUNT > source.term.get()) {
                //set leader term:
                getLeader().term.set(source.term.get());
                local.term.set(getLeader().term.get());
            } else {
                local.term.addAndGet(PUBLISH_TERM_INCREASE_COUNT);
            }

            RaftStore.updateTerm(local.term.get());
        }

    }

    public static class MasterElection implements Runnable {
        @Override
        public void run() {
            try {
                RaftPeer local = peers.local();
                local.leaderDueMs -= GlobalExecutor.TICK_PERIOD_MS;
                if (local.leaderDueMs > 0) {
                    return;
                }

                // reset timeout
                local.resetLeaderDue();
                local.resetHeartbeatDue();

                sendVote();
            } catch (Exception e) {
                Loggers.RAFT.warn("[RAFT] error while master election {}", e);
            }

        }

        public static void sendVote() {
            if (!initialized) {
                // not ready yet
                return;
            }

            RaftPeer local = peers.get(NetUtils.localServer());
            Loggers.RAFT.info("leader timeout, start voting,leader: {}, term: {}",
                JSON.toJSONString(getLeader()), local.term);

            peers.reset();

            local.term.incrementAndGet();
            local.voteFor = local.ip;
            local.state = RaftPeer.State.CANDIDATE;

            Map<String, String> params = new HashMap<String, String>(1);
            params.put("vote", JSON.toJSONString(local));
            for (final String server : peers.allServersWithoutMySelf()) {
                final String url = buildURL(server, API_VOTE);
                try {
                    HttpClient.asyncHttpPost(url, null, params, new AsyncCompletionHandler<Integer>() {
                        @Override
                        public Integer onCompleted(Response response) throws Exception {
                            if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                                Loggers.RAFT.error("NACOS-RAFT vote failed: {}, url: {}", response.getResponseBody(), url);
                                return 1;
                            }

                            RaftPeer peer = JSON.parseObject(response.getResponseBody(), RaftPeer.class);

                            Loggers.RAFT.info("received approve from peer: {}", JSON.toJSONString(peer));

                            peers.decideLeader(peer);

                            return 0;
                        }
                    });
                } catch (Exception e) {
                    Loggers.RAFT.warn("error while sending vote to server: {}", server);
                }
            }
        }

        public static RaftPeer receivedVote(RaftPeer remote) {
            if (!peers.contains(remote)) {
                throw new IllegalStateException("can not find peer: " + remote.ip);
            }

            if (!initialized) {
                throw new IllegalStateException("not ready yet");
            }

            RaftPeer local = peers.get(NetUtils.localServer());
            if (remote.term.get() <= local.term.get()) {
                String msg = "received illegitimate vote" +
                    ", voter-term:" + remote.term + ", votee-term:" + local.term;

                Loggers.RAFT.info(msg);
                if (StringUtils.isEmpty(local.voteFor)) {
                    local.voteFor = local.ip;
                }

                return local;
            }

            local.resetLeaderDue();

            local.state = RaftPeer.State.FOLLOWER;
            local.voteFor = remote.ip;
            local.term.set(remote.term.get());

            Loggers.RAFT.info("vote {} as leader, term: {}", remote.ip, remote.term);

            return local;
        }
    }

    public static class HeartBeat implements Runnable {
        @Override
        public void run() {
            try {
                RaftPeer local = peers.local();
                local.heartbeatDueMs -= GlobalExecutor.TICK_PERIOD_MS;
                if (local.heartbeatDueMs > 0) {
                    return;
                }

                local.resetHeartbeatDue();

                sendBeat();
            } catch (Exception e) {
                Loggers.RAFT.warn("[RAFT] error while sending beat {}", e);
            }

        }

        public static void sendBeat() throws IOException, InterruptedException {
            RaftPeer local = peers.local();
            if (local.state != RaftPeer.State.LEADER && !STANDALONE_MODE) {
                return;
            }

            Loggers.RAFT.info("[RAFT] send beat with {} keys.", datums.size());

            local.resetLeaderDue();

            // build data
            JSONObject packet = new JSONObject();
            packet.put("peer", local);

            JSONArray array = new JSONArray();

            if (Switch.isSendBeatOnly()) {
                Loggers.RAFT.info("[SEND-BEAT-ONLY] {}", String.valueOf(Switch.isSendBeatOnly()));
            }

            if (!Switch.isSendBeatOnly()) {
                for (Datum datum : datums.values()) {

                    JSONObject element = new JSONObject();
                    String key;

                    if (datum.key.startsWith(UtilsAndCommons.DOMAINS_DATA_ID_PRE)) {
                        key = (datum.key).split(UtilsAndCommons.DOMAINS_DATA_ID_PRE)[1];
                        element.put("key", UtilsAndCommons.RAFT_DOM_PRE + key);
                    } else if (datum.key.startsWith(UtilsAndCommons.IPADDRESS_DATA_ID_PRE)) {
                        key = (datum.key).split(UtilsAndCommons.IPADDRESS_DATA_ID_PRE)[1];
                        element.put("key", UtilsAndCommons.RAFT_IPLIST_PRE + key);
                    } else if (datum.key.startsWith(UtilsAndCommons.TAG_DOMAINS_DATA_ID)) {
                        key = (datum.key).split(UtilsAndCommons.TAG_DOMAINS_DATA_ID)[1];
                        element.put("key", UtilsAndCommons.RAFT_TAG_DOM_PRE + key);
                    } else if (datum.key.startsWith(UtilsAndCommons.NODE_TAG_IP_PRE)) {
                        key = (datum.key).split(UtilsAndCommons.NODE_TAG_IP_PRE)[1];
                        element.put("key", UtilsAndCommons.RAFT_TAG_IPLIST_PRE + key);
                    }
                    element.put("timestamp", datum.timestamp);

                    array.add(element);
                }
            } else {
                Loggers.RAFT.info("[RAFT] send beat only.");
            }

            packet.put("datums", array);
            // broadcast
            Map<String, String> params = new HashMap<String, String>(1);
            params.put("beat", JSON.toJSONString(packet));

            String content = JSON.toJSONString(params);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(out);
            gzip.write(content.getBytes("UTF-8"));
            gzip.close();

            byte[] compressedBytes = out.toByteArray();
            String compressedContent = new String(compressedBytes, "UTF-8");
            Loggers.RAFT.info("raw beat data size: {}, size of compressed data: {}",
                content.length(), compressedContent.length());

            for (final String server : peers.allServersWithoutMySelf()) {
                try {
                    final String url = buildURL(server, API_BEAT);
                    Loggers.RAFT.info("send beat to server " + server);
                    HttpClient.asyncHttpPostLarge(url, null, compressedBytes, new AsyncCompletionHandler<Integer>() {
                        @Override
                        public Integer onCompleted(Response response) throws Exception {
                            if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                                Loggers.RAFT.error("NACOS-RAFT beat failed: {}, peer: {}",
                                    response.getResponseBody(), server);
                                MetricsMonitor.getLeaderSendBeatFailedException().increment();
                                return 1;
                            }

                            peers.update(JSON.parseObject(response.getResponseBody(), RaftPeer.class));
                            Loggers.RAFT.info("receive beat response from: {}", url);
                            return 0;
                        }

                        @Override
                        public void onThrowable(Throwable t) {
                            Loggers.RAFT.error("NACOS-RAFT error while sending heart-beat to peer: {} {}", server, t);
                            MetricsMonitor.getLeaderSendBeatFailedException().increment();
                        }
                    });
                } catch (Exception e) {
                    Loggers.RAFT.error("VIPSRV error while sending heart-beat to peer: {} {}", server, e);
                    MetricsMonitor.getLeaderSendBeatFailedException().increment();
                }
            }

        }

        public static RaftPeer receivedBeat(JSONObject beat) throws Exception {
            final RaftPeer local = peers.local();
            final RaftPeer remote = new RaftPeer();
            remote.ip = beat.getJSONObject("peer").getString("ip");
            remote.state = RaftPeer.State.valueOf(beat.getJSONObject("peer").getString("state"));
            remote.term.set(beat.getJSONObject("peer").getLongValue("term"));
            remote.heartbeatDueMs = beat.getJSONObject("peer").getLongValue("heartbeatDueMs");
            remote.leaderDueMs = beat.getJSONObject("peer").getLongValue("leaderDueMs");
            remote.voteFor = beat.getJSONObject("peer").getString("voteFor");

            if (remote.state != RaftPeer.State.LEADER) {
                Loggers.RAFT.info("[RAFT] invalid state from master, state: {}, remote peer: {}",
                    remote.state, JSON.toJSONString(remote));
                throw new IllegalArgumentException("invalid state from master, state: " + remote.state);
            }

            if (local.term.get() > remote.term.get()) {
                Loggers.RAFT.info("[RAFT] out of date beat, beat-from-term: {}, beat-to-term: {}, remote peer: {}, and leaderDueMs: {}"
                    , remote.term.get(), local.term.get(), JSON.toJSONString(remote), local.leaderDueMs);
                throw new IllegalArgumentException("out of date beat, beat-from-term: " + remote.term.get()
                    + ", beat-to-term: " + local.term.get());
            }

            if (local.state != RaftPeer.State.FOLLOWER) {

                Loggers.RAFT.info("[RAFT] make remote as leader, remote peer: {}", JSON.toJSONString(remote));
                // mk follower
                local.state = RaftPeer.State.FOLLOWER;
                local.voteFor = remote.ip;
            }

            final JSONArray beatDatums = beat.getJSONArray("datums");
            local.resetLeaderDue();
            local.resetHeartbeatDue();

            peers.makeLeader(remote);

            Map<String, Integer> receivedKeysMap = new HashMap<String, Integer>(RaftCore.datums.size());

            for (Map.Entry<String, Datum> entry : RaftCore.datums.entrySet()) {
                receivedKeysMap.put(entry.getKey(), 0);
            }

            // now check datums
            List<String> batch = new ArrayList<String>();
            if (!Switch.isSendBeatOnly()) {
                int processedCount = 0;
                Loggers.RAFT.info("[RAFT] received beat with {} keys, RaftCore.datums' size is {}, remote server: {}, term: {}, local term: {}",
                    beatDatums.size(), RaftCore.datums.size(), remote.ip, remote.term, local.term);
                for (Object object : beatDatums) {
                    processedCount = processedCount + 1;

                    JSONObject entry = (JSONObject) object;
                    String key = entry.getString("key");
                    final String datumKey;

                    if (key.startsWith(UtilsAndCommons.RAFT_DOM_PRE)) {
                        int index = key.indexOf(UtilsAndCommons.RAFT_DOM_PRE);
                        datumKey = UtilsAndCommons.DOMAINS_DATA_ID_PRE + key.substring(index + UtilsAndCommons.RAFT_DOM_PRE.length());
                    } else if (key.startsWith(UtilsAndCommons.RAFT_IPLIST_PRE)) {
                        int index = key.indexOf(UtilsAndCommons.RAFT_IPLIST_PRE);
                        datumKey = UtilsAndCommons.IPADDRESS_DATA_ID_PRE + key.substring(index + UtilsAndCommons.RAFT_IPLIST_PRE.length());
                    } else if (key.startsWith(UtilsAndCommons.RAFT_TAG_DOM_PRE)) {
                        int index = key.indexOf(UtilsAndCommons.RAFT_TAG_DOM_PRE);
                        datumKey = UtilsAndCommons.TAG_DOMAINS_DATA_ID + key.substring(index + UtilsAndCommons.RAFT_TAG_DOM_PRE.length());
                    } else {
                        int index = key.indexOf(UtilsAndCommons.RAFT_TAG_IPLIST_PRE);
                        datumKey = UtilsAndCommons.NODE_TAG_IP_PRE + key.substring(index + UtilsAndCommons.RAFT_TAG_IPLIST_PRE.length());
                    }

                    long timestamp = entry.getLong("timestamp");

                    receivedKeysMap.put(datumKey, 1);

                    try {
                        if (RaftCore.datums.containsKey(datumKey) && RaftCore.datums.get(datumKey).timestamp.get() >= timestamp && processedCount < beatDatums.size()) {
                            continue;
                        }

                        if (!(RaftCore.datums.containsKey(datumKey) && RaftCore.datums.get(datumKey).timestamp.get() >= timestamp)) {
                            batch.add(datumKey);
                        }

                        if (batch.size() < 50 && processedCount < beatDatums.size()) {
                            continue;
                        }

                        String keys = StringUtils.join(batch, ",");

                        if (batch.size() <= 0) {
                            continue;
                        }

                        Loggers.RAFT.info("get datums from leader: {}, batch size is {}, processedCount is {}, datums' size is {}, RaftCore.datums' size is {}"
                            , getLeader().ip, batch.size(), processedCount, beatDatums.size(), RaftCore.datums.size());

                        // update datum entry
                        String url = buildURL(remote.ip, API_GET) + "?keys=" + URLEncoder.encode(keys, "UTF-8");
                        HttpClient.asyncHttpGet(url, null, null, new AsyncCompletionHandler<Integer>() {
                            @Override
                            public Integer onCompleted(Response response) throws Exception {
                                if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                                    return 1;
                                }

                                List<Datum> datumList = JSON.parseObject(response.getResponseBody(), new TypeReference<List<Datum>>() {
                                });

                                for (Datum datum : datumList) {
                                    OPERATE_LOCK.lock();
                                    try {

                                        Datum oldDatum = RaftCore.getDatum(datum.key);

                                        if (oldDatum != null && datum.timestamp.get() <= oldDatum.timestamp.get()) {
                                            Loggers.RAFT.info("[NACOS-RAFT] timestamp is smaller than that of mine, key: {}, remote: {}, local: {}",
                                                datum.key, datum.timestamp, oldDatum.timestamp);
                                            continue;
                                        }

                                        if (datum.key.startsWith(UtilsAndCommons.DOMAINS_DATA_ID_PRE) ||
                                            UtilsAndCommons.INSTANCE_LIST_PERSISTED) {
                                            RaftStore.write(datum);
                                        }

                                        RaftCore.datums.put(datum.key, datum);
                                        local.resetLeaderDue();

                                        if (datum.key.startsWith(UtilsAndCommons.DOMAINS_DATA_ID_PRE)) {
                                            if (local.term.get() + 100 > remote.term.get()) {
                                                getLeader().term.set(remote.term.get());
                                                local.term.set(getLeader().term.get());
                                            } else {
                                                local.term.addAndGet(100);
                                            }

                                            RaftStore.updateTerm(local.term.get());
                                        }

                                        Loggers.RAFT.info("data updated, key: {}, timestamp: {}, from {}, local term: {}",
                                            datum.key, datum.timestamp, JSON.toJSONString(remote), local.term);

                                        notifier.addTask(datum, Notifier.ApplyAction.CHANGE);
                                    } catch (Throwable e) {
                                        Loggers.RAFT.error("[RAFT-BEAT] failed to sync datum from leader, key: {} {}", datum.key, e);
                                    } finally {
                                        OPERATE_LOCK.unlock();
                                    }
                                }
                                TimeUnit.MILLISECONDS.sleep(200);
                                return 0;
                            }
                        });

                        batch.clear();

                    } catch (Exception e) {
                        Loggers.RAFT.error("[NACOS-RAFT] failed to handle beat entry, key: {}", datumKey);
                    }

                }

                List<String> deadKeys = new ArrayList<String>();
                for (Map.Entry<String, Integer> entry : receivedKeysMap.entrySet()) {
                    if (entry.getValue() == 0) {
                        deadKeys.add(entry.getKey());
                    }
                }

                for (String deadKey : deadKeys) {
                    try {
                        deleteDatum(deadKey);
                    } catch (Exception e) {
                        Loggers.RAFT.error("[NACOS-RAFT] failed to remove entry, key={} {}", deadKey, e);
                    }
                }

            }


            return local;
        }
    }

    public static class AddressServerUpdater implements Runnable {
        @Override
        public void run() {
            try {
                List<String> servers = NamingProxy.getServers();
                List<RaftPeer> peerList = new ArrayList<RaftPeer>(peers.allPeers());
                List<String> oldServers = new ArrayList<String>();

                if (CollectionUtils.isEmpty(servers)) {
                    Loggers.RAFT.warn("get empty server list from address server,ignore it.");
                    return;
                }

                for (RaftPeer peer : peerList) {
                    oldServers.add(peer.ip);
                }

                List<String> newServers = (List<String>) CollectionUtils.subtract(servers, oldServers);
                if (!CollectionUtils.isEmpty(newServers)) {
                    peers.add(newServers);
                    Loggers.RAFT.info("server list is updated, new: {} servers: {}", newServers.size(), newServers);
                }

                List<String> deadServers = (List<String>) CollectionUtils.subtract(oldServers, servers);
                if (!CollectionUtils.isEmpty(deadServers)) {
                    peers.remove(deadServers);
                    Loggers.RAFT.info("server list is updated, dead: {}, servers: {}", deadServers.size(), deadServers);
                }
            } catch (Exception e) {
                Loggers.RAFT.info("error while updating server list.", e);
            }
        }
    }

    public static void listen(RaftListener listener) {
        if (listeners.contains(listener)) {
            return;
        }

        listeners.add(listener);

        for (RaftListener listener1 : listeners) {
            if (listener1 instanceof VirtualClusterDomain) {
                Loggers.RAFT.debug("listener in listeners: {}", ((VirtualClusterDomain) listener1).getName());
            }
        }

        if (listeners.contains(listener)) {
            if (listener instanceof VirtualClusterDomain) {
                Loggers.RAFT.info("add listener: {}", ((VirtualClusterDomain) listener).getName());
            } else {
                Loggers.RAFT.info("add listener for switch or domain meta. ");
            }
        } else {
            Loggers.RAFT.error("[NACOS-RAFT] faild to add listener: {}", JSON.toJSONString(listener));
        }
        // if data present, notify immediately
        for (Datum datum : datums.values()) {
            if (!listener.interests(datum.key)) {
                continue;
            }

            try {
                listener.onChange(datum.key, datum.value);
            } catch (Exception e) {
                Loggers.RAFT.error("NACOS-RAFT failed to notify listener", e);
            }
        }
    }

    public static void unlisten(String key) {
        for (RaftListener listener : listeners) {
            if (listener.matchUnlistenKey(key)) {
                listeners.remove(listener);
            }
        }
    }

    public static void setTerm(long term) {
        RaftCore.peers.setTerm(term);
    }

    public static long getTerm() {
        return RaftCore.peers.getTerm();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isLeader(String ip) {
        return peers.isLeader(ip);
    }

    public static boolean isLeader() {
        return peers.isLeader(NetUtils.localServer());
    }

    public static String buildURL(String ip, String api) {
        if (!ip.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
            ip = ip + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
        }
        return "http://" + ip + RunningConfig.getContextPath() + api;
    }

    public static Datum getDatum(String key) {
        return datums.get(key);
    }

    public static RaftPeer getLeader() {
        return peers.getLeader();
    }

    public static List<RaftPeer> getPeers() {
        return new ArrayList<RaftPeer>(peers.allPeers());
    }

    public static PeerSet getPeerSet() {
        return peers;
    }

    public static void setPeerSet(PeerSet peerSet) {
        peers = peerSet;
    }

    public static int datumSize() {
        return datums.size();
    }

    public static void addDatum(Datum datum) {
        datums.put(datum.key, datum);
        notifier.addTask(datum, Notifier.ApplyAction.CHANGE);
    }

    private static void deleteDatum(String key) {

        Datum deleted = null;
        try {
            deleted = datums.remove(URLDecoder.decode(key, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Loggers.RAFT.warn("datum key decode failed: {}", key);
        }
        if (deleted != null) {
            RaftStore.delete(deleted);
            notifier.addTask(deleted, Notifier.ApplyAction.DELETE);
            Loggers.RAFT.info("datum deleted, key: {}", key);
        }
    }

    public static class Notifier implements Runnable {

        private ConcurrentHashMap<String, String> services = new ConcurrentHashMap<>(10 * 1024);

        private BlockingQueue<Pair> tasks = new LinkedBlockingQueue<Pair>(1024 * 1024);

        public void addTask(Datum datum, ApplyAction action) {

            if (services.containsKey(datum.key) && action == ApplyAction.CHANGE) {
                return;
            }
            if (action == ApplyAction.CHANGE) {
                services.put(datum.key, StringUtils.EMPTY);
            }
            tasks.add(Pair.with(datum, action));
        }

        public int getTaskSize() {
            return tasks.size();
        }

        @Override
        public void run() {
            Loggers.RAFT.info("raft notifier started");

            while (true) {
                try {

                    Pair pair = tasks.take();

                    if (pair == null) {
                        continue;
                    }

                    Datum datum = (Datum) pair.getValue0();
                    ApplyAction action = (ApplyAction) pair.getValue1();

                    services.remove(datum.key);

                    int count = 0;
                    for (RaftListener listener : listeners) {

                        if (listener instanceof VirtualClusterDomain) {
                            if (Loggers.RAFT.isDebugEnabled()) {
                                Loggers.RAFT.debug("listener: " + ((VirtualClusterDomain) listener).getName());
                            }
                        }

                        if (!listener.interests(datum.key)) {
                            continue;
                        }

                        count++;

                        try {
                            if (action == ApplyAction.CHANGE) {
                                listener.onChange(datum.key, getDatum(datum.key).value);
                                continue;
                            }

                            if (action == ApplyAction.DELETE) {
                                listener.onDelete(datum.key, datum.value);
                                continue;
                            }
                        } catch (Throwable e) {
                            Loggers.RAFT.error("[NACOS-RAFT] error while notifying listener of key: {} {}", datum.key, e);
                        }
                    }

                    if (Loggers.RAFT.isDebugEnabled()) {
                        Loggers.RAFT.debug("[NACOS-RAFT] datum change notified, key: {}, listener count: {}", datum.key, count);
                    }
                } catch (Throwable e) {
                    Loggers.RAFT.error("[NACOS-RAFT] Error while handling notifying task", e);
                }
            }
        }

        public enum ApplyAction {
            /**
             * Data changed
             */
            CHANGE,
            /**
             * Data deleted
             */
            DELETE
        }
    }
}
