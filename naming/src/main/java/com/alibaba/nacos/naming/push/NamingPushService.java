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
package com.alibaba.nacos.naming.push;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.naming.NamingSuscribeType;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.core.remoting.ConnectionManager;
import com.alibaba.nacos.core.remoting.PushManager;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.apache.commons.collections.MapUtils;
import org.codehaus.jackson.util.VersionUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

/**
 * @author nacos
 */
@Component
public class NamingPushService implements ApplicationContextAware, ApplicationListener<ServiceChangeEvent> {

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private ConnectionManager connectionManager;

    @Autowired
    private PushManager pushManager;

    private ApplicationContext applicationContext;

    private static final long ACK_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10L);

    private static final int MAX_RETRY_TIMES = 1;

    private static volatile ConcurrentMap<String, Receiver.AckEntry> ackMap
        = new ConcurrentHashMap<String, Receiver.AckEntry>();

    private static ConcurrentMap<String, ConcurrentMap<String, PushClient>> clientMap
        = new ConcurrentHashMap<String, ConcurrentMap<String, PushClient>>();

    private static volatile ConcurrentHashMap<String, Long> udpSendTimeMap = new ConcurrentHashMap<String, Long>();

    public static volatile ConcurrentHashMap<String, Long> pushCostMap = new ConcurrentHashMap<String, Long>();

    private static int totalPush = 0;

    private static int failedPush = 0;

    private static ConcurrentHashMap<String, Long> lastPushMillisMap = new ConcurrentHashMap<>();

    private static DatagramSocket udpSocket;

    private static Map<String, Future> futureMap = new ConcurrentHashMap<>();
    private static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("com.alibaba.nacos.naming.push.retransmitter");
            return t;
        }
    });

    private static ScheduledExecutorService udpSender = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("com.alibaba.nacos.naming.push.udpSender");
            return t;
        }
    });

    @PostConstruct
    public void init() {
        try {
            udpSocket = new DatagramSocket();

            Receiver receiver = new Receiver();

            Thread inThread = new Thread(receiver);
            inThread.setDaemon(true);
            inThread.setName("com.alibaba.nacos.naming.push.receiver");
            inThread.start();

            executorService.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        removeClientIfZombie();
                    } catch (Throwable e) {
                        Loggers.PUSH.warn("[NACOS-PUSH] failed to remove client zombie");
                    }
                }
            }, 0, 20, TimeUnit.SECONDS);

        } catch (SocketException e) {
            Loggers.SRV_LOG.error("[NACOS-PUSH] failed to init push service");
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ServiceChangeEvent event) {
        Service service = event.getService();
        String serviceName = service.getName();
        String namespaceId = service.getNamespaceId();

        Future future = udpSender.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    Loggers.PUSH.info(serviceName + " is changed, add it to push queue.");
                    ConcurrentMap<String, PushClient> clients = clientMap.get(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));
                    if (MapUtils.isEmpty(clients)) {
                        return;
                    }

                    Map<String, Object> cache = new HashMap<>(16);
                    long lastRefTime = System.nanoTime();
                    for (PushClient client : clients.values()) {

                        if (isZombie(client)) {
                            Loggers.PUSH.debug("client is zombie: " + client.toString());
                            clients.remove(client.toString());
                            Loggers.PUSH.debug("client is zombie: " + client.toString());
                            continue;
                        }

                        Receiver.AckEntry ackEntry;
                        Loggers.PUSH.debug("push serviceName: {} to client: {}", serviceName, client.toString());
                        String key = getPushCacheKey(serviceName, client);
                        byte[] compressData = null;
                        Map<String, Object> data = null;
                        if (switchDomain.getDefaultPushCacheMillis() >= 20000 && cache.containsKey(key)) {
                            org.javatuples.Pair pair = (org.javatuples.Pair) cache.get(key);
                            // TODO
                            compressData = (byte[]) (pair.getValue0());
                            data = (Map<String, Object>) pair.getValue1();

                            Loggers.PUSH.debug("[PUSH-CACHE] cache hit: {}:{}", serviceName, client.getKey());
                        }

                        if (compressData != null) {
                            ackEntry = prepareAckEntry(client, compressData, data, lastRefTime);
                        } else {
                            ackEntry = prepareAckEntry(client, prepareHostsData(client), lastRefTime);
                            if (ackEntry != null) {
                                if (client.getType().equals(NamingSuscribeType.UDP.name())) {
                                    cache.put(key, new org.javatuples.Pair<>(((DatagramPacket) ackEntry.origin).getData(), ackEntry.data));
                                } else {
                                    cache.put(key, new org.javatuples.Pair<>(ackEntry.origin, ackEntry.data));
                                }
                            }
                        }

                        Loggers.PUSH.info("serviceName: {} changed, schedule push for: {}, agent: {}, key: {}",
                            client.getServiceName(), client.getKey(), client.getAgent(), (ackEntry == null ? null : ackEntry.key));

                        if (client.getType().equals(NamingSuscribeType.GRPC.name())) {
                            PushClient.GrpcPushClient grpcClient = (PushClient.GrpcPushClient) client;
                            pushManager.pushChange(grpcClient.getClientId(), grpcClient.getServiceName(), (byte[]) ackEntry.origin);
                        } else {
                            udpPush(ackEntry);
                        }

                    }
                } catch (Exception e) {
                    Loggers.PUSH.error("[NACOS-PUSH] failed to push serviceName: {} to client, error: {}", serviceName, e);

                } finally {
                    futureMap.remove(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));
                }

            }
        }, 1000, TimeUnit.MILLISECONDS);

        futureMap.put(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName), future);

    }

    public int getTotalPush() {
        return totalPush;
    }

    public void setTotalPush(int totalPush) {
        NamingPushService.totalPush = totalPush;
    }

    public void addGrpcClient(com.alibaba.nacos.naming.push.PushClient.GrpcPushClient grpcPushClient) {
        addClient(grpcPushClient);
    }

    public void removeGrpcClient(com.alibaba.nacos.naming.push.PushClient.GrpcPushClient grpcPushClient) {
        // remove grpc client:
        String serviceKey = UtilsAndCommons.assembleFullServiceName(grpcPushClient.getNamespaceId(), grpcPushClient.getServiceName());
        if (!clientMap.containsKey(serviceKey)) {
            return;
        }
        clientMap.get(serviceKey).remove(grpcPushClient.getKey());
    }

    public void addUdpClient(String namespaceId,
                             String serviceName,
                             String clusters,
                             String agent,
                             InetSocketAddress socketAddr,
                             DataSource dataSource,
                             String tenant,
                             String app) {

        PushClient.UdpPushClient client = new PushClient.UdpPushClient(namespaceId,
            serviceName,
            clusters,
            agent,
            socketAddr,
            dataSource,
            tenant,
            app);

        addClient(client);
    }

    public void addClient(PushClient client) {
        // client is stored by key 'serviceName' because notify event is driven by serviceName change
        String serviceKey = UtilsAndCommons.assembleFullServiceName(client.getNamespaceId(), client.getServiceName());
        ConcurrentMap<String, PushClient> clients =
            clientMap.get(serviceKey);
        if (clients == null) {
            clientMap.putIfAbsent(serviceKey, new ConcurrentHashMap<String, PushClient>(1024));
            clients = clientMap.get(serviceKey);
        }

        PushClient oldClient = clients.get(client.getKey());
        if (oldClient != null) {
            oldClient.refresh();
        } else {
            PushClient res = clients.putIfAbsent(client.getKey(), client);
            if (res != null) {
                Loggers.PUSH.warn("client: {} already associated with key {}", res.getKey(), res.toString());
            }
            Loggers.PUSH.debug("client: {} added for serviceName: {}", client.getKey(), client.getServiceName());
        }
    }

    public List<Subscriber> getClients(String serviceName, String namespaceId) {
        String serviceKey = UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName);
        ConcurrentMap<String, PushClient> clientConcurrentMap = clientMap.get(serviceKey);
        if (Objects.isNull(clientConcurrentMap)) {
            return null;
        }
        List<Subscriber> clients = new ArrayList<Subscriber>();
        clientConcurrentMap.forEach((key, client) -> {
            clients.add(new Subscriber(client.getKey(), client.getAgent(), namespaceId, serviceName, client.getMetadata()));
        });
        return clients;
    }

    /**
     * fuzzy search subscriber
     *
     * @param serviceName
     * @param namespaceId
     * @return
     */
    public List<Subscriber> getClientsFuzzy(String serviceName, String namespaceId) {
        List<Subscriber> clients = new ArrayList<Subscriber>();
        clientMap.forEach((outKey, clientConcurrentMap) -> {
            //get groupedName from key
            String serviceFullName = outKey.split(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)[1];
            //get groupName
            String groupName = NamingUtils.getGroupName(serviceFullName);
            //get serviceName
            String name = NamingUtils.getServiceName(serviceFullName);
            //fuzzy match
            if (outKey.startsWith(namespaceId) && name.indexOf(NamingUtils.getServiceName(serviceName)) >= 0 && groupName.indexOf(NamingUtils.getGroupName(serviceName)) >= 0) {
                clientConcurrentMap.forEach((key, client) -> {
                    clients.add(new Subscriber(client.getKey(), client.getAgent(), namespaceId, serviceFullName, client.getMetadata()));
                });
            }
        });
        return clients;
    }

    private boolean isZombie(PushClient pushClient) {

        if (pushClient.getType().equals(NamingSuscribeType.UDP.name())) {
            return System.currentTimeMillis() - pushClient.getLastRefTime() > switchDomain.getPushCacheMillis(pushClient.getServiceName());
        }

        if (pushClient.getType().equals(NamingSuscribeType.GRPC.name())) {
            return !connectionManager.hasConnection(((PushClient.GrpcPushClient) pushClient).getClientId());
        }

        return true;
    }

    public void removeClientIfZombie() {

        int size = 0;
        for (Map.Entry<String, ConcurrentMap<String, PushClient>> entry : clientMap.entrySet()) {
            ConcurrentMap<String, PushClient> clientConcurrentMap = entry.getValue();
            for (Map.Entry<String, PushClient> entry1 : clientConcurrentMap.entrySet()) {
                PushClient client = entry1.getValue();
                if (isZombie(client)) {
                    clientConcurrentMap.remove(entry1.getKey());
                }
            }

            size += clientConcurrentMap.size();
        }

        if (Loggers.PUSH.isDebugEnabled()) {
            Loggers.PUSH.debug("[NACOS-PUSH] clientMap size: {}", size);
        }

    }

    private static Receiver.AckEntry prepareAckEntry(PushClient client, byte[] dataBytes, Map<String, Object> data,
                                                     long lastRefTime) {

        String key = getACKKey(client.getKey(), lastRefTime);
        try {
            Receiver.AckEntry ackEntry;
            if (client.getType().equals(NamingSuscribeType.UDP.name())) {
                DatagramPacket packet = new DatagramPacket(dataBytes, dataBytes.length, ((PushClient.UdpPushClient) client).getSocketAddr());
                ackEntry = new Receiver.AckEntry(key, packet);
                ackEntry.data = data;
            } else {
                ackEntry = new Receiver.AckEntry(key, dataBytes);
                ackEntry.data = data;
            }

            return ackEntry;
        } catch (Exception e) {
            Loggers.PUSH.error("[NACOS-PUSH] failed to prepare data: {} to client: {}, error: {}",
                data, client.getKey(), e);
        }

        return null;
    }

    public static String getPushCacheKey(String serviceName, PushClient pushClient) {
        return serviceName + UtilsAndCommons.CACHE_KEY_SPLITER + pushClient.getKey();
    }

    public void serviceChanged(Service service) {
        // merge some change events to reduce the push frequency:
        if (futureMap.containsKey(UtilsAndCommons.assembleFullServiceName(service.getNamespaceId(), service.getName()))) {
            return;
        }

        this.applicationContext.publishEvent(new ServiceChangeEvent(this, service));
    }

    public boolean canEnablePush(String agent) {

        if (!switchDomain.isPushEnabled()) {
            return false;
        }

        ClientInfo clientInfo = new ClientInfo(agent);

        if (ClientInfo.ClientType.JAVA == clientInfo.type
            && clientInfo.version.compareTo(VersionUtil.parseVersion(switchDomain.getPushJavaVersion())) >= 0) {
            return true;
        } else if (ClientInfo.ClientType.DNS == clientInfo.type
            && clientInfo.version.compareTo(VersionUtil.parseVersion(switchDomain.getPushPythonVersion())) >= 0) {
            return true;
        } else if (ClientInfo.ClientType.C == clientInfo.type
            && clientInfo.version.compareTo(VersionUtil.parseVersion(switchDomain.getPushCVersion())) >= 0) {
            return true;
        } else if (ClientInfo.ClientType.GO == clientInfo.type
            && clientInfo.version.compareTo(VersionUtil.parseVersion(switchDomain.getPushGoVersion())) >= 0) {
            return true;
        }

        return false;
    }

    public static List<Receiver.AckEntry> getFailedPushes() {
        return new ArrayList<Receiver.AckEntry>(ackMap.values());
    }

    public int getFailedPushCount() {
        return ackMap.size() + failedPush;
    }

    public void setFailedPush(int failedPush) {
        NamingPushService.failedPush = failedPush;
    }


    public static void resetPushState() {
        ackMap.clear();
    }

    private static byte[] compressIfNecessary(byte[] dataBytes) throws IOException {
        // enable compression when data is larger than 1KB
        int maxDataSizeUncompress = 1024;
        if (dataBytes.length < maxDataSizeUncompress) {
            return dataBytes;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(dataBytes);
        gzip.close();

        return out.toByteArray();
    }

    private static Map<String, Object> prepareHostsData(PushClient client) throws Exception {
        Map<String, Object> cmd = new HashMap<String, Object>(2);
        cmd.put("type", "dom");
        cmd.put("data", client.getDataSource().getData(client));

        return cmd;
    }

    private static Receiver.AckEntry prepareAckEntry(PushClient client, Map<String, Object> data, long lastRefTime) {
        if (MapUtils.isEmpty(data)) {
            Loggers.PUSH.error("[NACOS-PUSH] pushing empty data for client is not allowed: {}", client);
            return null;
        }

        data.put("lastRefTime", lastRefTime);

        // we apply lastRefTime as sequence num for further ack
        String key = getACKKey(client.getKey(), lastRefTime);

        String dataStr = JSON.toJSONString(data);

        try {
            byte[] dataBytes = dataStr.getBytes(StandardCharsets.UTF_8);
            dataBytes = compressIfNecessary(dataBytes);

            // we must store the key be fore send, otherwise there will be a chance the
            // ack returns before we put in
            Receiver.AckEntry ackEntry;
            if (client.getType().equals(NamingSuscribeType.UDP.name())) {
                DatagramPacket packet = new DatagramPacket(dataBytes, dataBytes.length, ((PushClient.UdpPushClient) client).getSocketAddr());
                ackEntry = new Receiver.AckEntry(key, packet);
                ackEntry.data = data;
            } else {
                ackEntry = new Receiver.AckEntry(key, dataBytes);
                ackEntry.data = data;
            }

            return ackEntry;
        } catch (Exception e) {
            Loggers.PUSH.error("[NACOS-PUSH] failed to prepare data: {} to client: {}, error: {}",
                data, client.getKey(), e);
            return null;
        }
    }

    private static Receiver.AckEntry udpPush(Receiver.AckEntry ackEntry) {
        if (ackEntry == null) {
            Loggers.PUSH.error("[NACOS-PUSH] ackEntry is null.");
            return null;
        }

        if (ackEntry.getRetryTimes() > MAX_RETRY_TIMES) {
            Loggers.PUSH.warn("max re-push times reached, retry times {}, key: {}", ackEntry.retryTimes, ackEntry.key);
            ackMap.remove(ackEntry.key);
            udpSendTimeMap.remove(ackEntry.key);
            failedPush += 1;
            return ackEntry;
        }

        try {
            if (!ackMap.containsKey(ackEntry.key)) {
                totalPush++;
            }
            ackMap.put(ackEntry.key, ackEntry);
            udpSendTimeMap.put(ackEntry.key, System.currentTimeMillis());

            Loggers.PUSH.info("send udp packet: " + ackEntry.key);
            udpSocket.send((DatagramPacket) ackEntry.origin);

            ackEntry.increaseRetryTime();

            executorService.schedule(new Retransmitter(ackEntry), TimeUnit.NANOSECONDS.toMillis(ACK_TIMEOUT_NANOS),
                TimeUnit.MILLISECONDS);

            return ackEntry;
        } catch (Exception e) {
            Loggers.PUSH.error("[NACOS-PUSH] failed to push data: {} to client: {}, error: {}",
                ackEntry.data, ((DatagramPacket) ackEntry.origin).getAddress().getHostAddress(), e);
            ackMap.remove(ackEntry.key);
            udpSendTimeMap.remove(ackEntry.key);
            failedPush += 1;

            return null;
        }
    }

    private static String getACKKey(String address, long lastRefTime) {
        return address + "," + lastRefTime;
    }

    public static class Retransmitter implements Runnable {
        Receiver.AckEntry ackEntry;

        public Retransmitter(Receiver.AckEntry ackEntry) {
            this.ackEntry = ackEntry;
        }

        @Override
        public void run() {
            if (ackMap.containsKey(ackEntry.key)) {
                Loggers.PUSH.info("retry to push data, key: " + ackEntry.key);
                udpPush(ackEntry);
            }
        }
    }

    public static class Receiver implements Runnable {
        @Override
        public void run() {
            while (true) {
                byte[] buffer = new byte[1024 * 64];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                try {
                    udpSocket.receive(packet);

                    String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    AckPacket ackPacket = JSON.parseObject(json, AckPacket.class);

                    InetSocketAddress socketAddress = (InetSocketAddress) packet.getSocketAddress();
                    String ip = socketAddress.getAddress().getHostAddress();
                    int port = socketAddress.getPort();

                    if (System.nanoTime() - ackPacket.lastRefTime > ACK_TIMEOUT_NANOS) {
                        Loggers.PUSH.warn("ack takes too long from {} ack json: {}", packet.getSocketAddress(), json);
                    }

                    String ackKey = getACKKey(ip + ":" + port, ackPacket.lastRefTime);
                    AckEntry ackEntry = ackMap.remove(ackKey);
                    if (ackEntry == null) {
                        throw new IllegalStateException("unable to find ackEntry for key: " + ackKey
                            + ", ack json: " + json);
                    }

                    long pushCost = System.currentTimeMillis() - udpSendTimeMap.get(ackKey);

                    Loggers.PUSH.info("received ack: {} from: {}:{}, cost: {} ms, unacked: {}, total push: {}",
                        json, ip, port, pushCost, ackMap.size(), totalPush);

                    pushCostMap.put(ackKey, pushCost);

                    udpSendTimeMap.remove(ackKey);

                } catch (Throwable e) {
                    Loggers.PUSH.error("[NACOS-PUSH] error while receiving ack data", e);
                }
            }
        }

        public static class AckEntry {

            public AckEntry(String key, Object packet) {
                this.key = key;
                this.origin = packet;
            }

            public void increaseRetryTime() {
                retryTimes.incrementAndGet();
            }

            public int getRetryTimes() {
                return retryTimes.get();
            }

            public String key;
            public Object origin;
            private AtomicInteger retryTimes = new AtomicInteger(0);
            public Map<String, Object> data;
        }

        public static class AckPacket {
            public String type;
            public long lastRefTime;

            public String data;
        }
    }


}
