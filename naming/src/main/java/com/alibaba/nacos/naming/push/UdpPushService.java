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

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.v1.ClientInfo;
import com.alibaba.nacos.naming.push.v1.NamingSubscriberServiceV1Impl;
import com.alibaba.nacos.naming.push.v1.PushClient;
import com.alibaba.nacos.naming.push.v1.ServiceChangeEvent;
import com.alibaba.nacos.naming.remote.udp.AckEntry;
import com.alibaba.nacos.naming.remote.udp.AckPacket;
import com.alibaba.nacos.naming.remote.udp.UdpConnector;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.apache.commons.collections.MapUtils;
import org.codehaus.jackson.util.VersionUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * Push service.
 *
 * @author nacos
 */
@Component
@SuppressWarnings("PMD.ThreadPoolCreationRule")
public class UdpPushService implements ApplicationContextAware, ApplicationListener<ServiceChangeEvent> {
    
    @Autowired
    private SwitchDomain switchDomain;
    
    @Autowired
    private NamingSubscriberServiceV1Impl subscriberServiceV1;
    
    private ApplicationContext applicationContext;
    
    private static volatile ConcurrentMap<String, AckEntry> ackMap = new ConcurrentHashMap<>();
    
    private static volatile ConcurrentMap<String, Long> udpSendTimeMap = new ConcurrentHashMap<>();
    
    private static DatagramSocket udpSocket;
    
    private final UdpConnector udpConnector;
    
    private static ConcurrentMap<String, Future> futureMap = new ConcurrentHashMap<>();
    
    static {
        try {
            udpSocket = new DatagramSocket();
            
            Receiver receiver = new Receiver();
            
            Thread inThread = new Thread(receiver);
            inThread.setDaemon(true);
            inThread.setName("com.alibaba.nacos.naming.push.receiver");
            inThread.start();
            
        } catch (SocketException e) {
            Loggers.SRV_LOG.error("[NACOS-PUSH] failed to init push service");
        }
    }
    
    public UdpPushService(UdpConnector udpConnector) {
        this.udpConnector = udpConnector;
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public void onApplicationEvent(ServiceChangeEvent event) {
        // If upgrade to 2.0.X, do not push for v1.
        if (ApplicationUtils.getBean(UpgradeJudgement.class).isUseGrpcFeatures()) {
            return;
        }
        Service service = event.getService();
        String serviceName = service.getName();
        String namespaceId = service.getNamespaceId();
        //merge some change events to reduce the push frequency:
        if (futureMap.containsKey(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName))) {
            return;
        }
        Future future = GlobalExecutor.scheduleUdpSender(() -> {
            try {
                Loggers.PUSH.info(serviceName + " is changed, add it to push queue.");
                ConcurrentMap<String, PushClient> clients = subscriberServiceV1.getClientMap()
                        .get(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));
                if (MapUtils.isEmpty(clients)) {
                    return;
                }
                
                Map<String, Object> cache = new HashMap<>(16);
                long lastRefTime = System.nanoTime();
                for (PushClient client : clients.values()) {
                    if (client.zombie()) {
                        Loggers.PUSH.debug("client is zombie: " + client);
                        clients.remove(client.toString());
                        Loggers.PUSH.debug("client is zombie: " + client);
                        continue;
                    }
                    
                    AckEntry ackEntry;
                    Loggers.PUSH.debug("push serviceName: {} to client: {}", serviceName, client);
                    String key = getPushCacheKey(serviceName, client.getIp(), client.getAgent());
                    byte[] compressData = null;
                    Map<String, Object> data = null;
                    if (switchDomain.getDefaultPushCacheMillis() >= 20000 && cache.containsKey(key)) {
                        org.javatuples.Pair pair = (org.javatuples.Pair) cache.get(key);
                        compressData = (byte[]) (pair.getValue0());
                        data = (Map<String, Object>) pair.getValue1();
                        
                        Loggers.PUSH.debug("[PUSH-CACHE] cache hit: {}:{}", serviceName, client.getAddrStr());
                    }
                    
                    if (compressData != null) {
                        ackEntry = prepareAckEntry(client, compressData, data, lastRefTime);
                    } else {
                        ackEntry = prepareAckEntry(client, prepareHostsData(client), lastRefTime);
                        if (ackEntry != null) {
                            cache.put(key,
                                    new org.javatuples.Pair<>(ackEntry.getOrigin().getData(), ackEntry.getData()));
                        }
                    }
                    
                    Loggers.PUSH.info("serviceName: {} changed, schedule push for: {}, agent: {}, key: {}",
                            client.getServiceName(), client.getAddrStr(), client.getAgent(),
                            (ackEntry == null ? null : ackEntry.getKey()));
                    
                    udpPush(ackEntry);
                }
            } catch (Exception e) {
                Loggers.PUSH.error("[NACOS-PUSH] failed to push serviceName: {} to client, error: {}", serviceName, e);
                
            } finally {
                futureMap.remove(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));
            }
            
        }, 1000, TimeUnit.MILLISECONDS);
        
        futureMap.put(UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName), future);
        
    }
    
    /**
     * Push Data without callback.
     *
     * @param subscriber  subscriber
     * @param serviceInfo service info
     */
    public void pushDataWithoutCallback(Subscriber subscriber, ServiceInfo serviceInfo) {
        String serviceName = subscriber.getServiceName();
        try {
            Loggers.PUSH.info(serviceName + " is changed, add it to push queue.");
            AckEntry ackEntry = prepareAckEntry(subscriber, serviceInfo);
            Loggers.PUSH.info("serviceName: {} changed, schedule push for: {}, agent: {}, key: {}", serviceInfo,
                    subscriber.getAddrStr(), subscriber.getAgent(), (ackEntry == null ? null : ackEntry.getKey()));
            udpConnector.sendData(ackEntry);
        } catch (Exception e) {
            Loggers.PUSH.error("[NACOS-PUSH] failed to push serviceName: {} to client, error: {}", serviceName, e);
        }
    }
    
    /**
     * Push Data with callback.
     *
     * @param subscriber   subscriber
     * @param serviceInfo  service info
     * @param pushCallBack callback
     */
    public void pushDataWithCallback(Subscriber subscriber, ServiceInfo serviceInfo, PushCallBack pushCallBack) {
        String serviceName = subscriber.getServiceName();
        try {
            Loggers.PUSH.info(serviceName + " is changed, add it to push queue.");
            AckEntry ackEntry = prepareAckEntry(subscriber, serviceInfo);
            Loggers.PUSH.info("serviceName: {} changed, schedule push for: {}, agent: {}, key: {}", serviceInfo,
                    subscriber.getAddrStr(), subscriber.getAgent(), (ackEntry == null ? null : ackEntry.getKey()));
            udpConnector.sendDataWithCallback(ackEntry, pushCallBack);
        } catch (Exception e) {
            Loggers.PUSH.error("[NACOS-PUSH] failed to push serviceName: {} to client, error: {}", serviceName, e);
        }
    }
    
    private AckEntry prepareAckEntry(Subscriber subscriber, ServiceInfo serviceInfo) {
        InetSocketAddress socketAddress = new InetSocketAddress(subscriber.getIp(), subscriber.getPort());
        long lastRefTime = System.nanoTime();
        return prepareAckEntry(socketAddress, prepareHostsData(JacksonUtils.toJson(serviceInfo)), lastRefTime);
    }
    
    private static AckEntry prepareAckEntry(PushClient client, Map<String, Object> data, long lastRefTime) {
        return prepareAckEntry(client.getSocketAddr(), data, lastRefTime);
    }
    
    private static AckEntry prepareAckEntry(InetSocketAddress socketAddress, Map<String, Object> data,
            long lastRefTime) {
        if (MapUtils.isEmpty(data)) {
            Loggers.PUSH.error("[NACOS-PUSH] pushing empty data for client is not allowed: {}", socketAddress);
            return null;
        }
        data.put("lastRefTime", lastRefTime);
        String dataStr = JacksonUtils.toJson(data);
        try {
            byte[] dataBytes = dataStr.getBytes(StandardCharsets.UTF_8);
            dataBytes = compressIfNecessary(dataBytes);
            return prepareAckEntry(socketAddress, dataBytes, data, lastRefTime);
        } catch (Exception e) {
            Loggers.PUSH
                    .error("[NACOS-PUSH] failed to compress data: {} to client: {}, error: {}", data, socketAddress, e);
            return null;
        }
    }
    
    private static AckEntry prepareAckEntry(PushClient client, byte[] dataBytes, Map<String, Object> data,
            long lastRefTime) {
        return prepareAckEntry(client.getSocketAddr(), dataBytes, data, lastRefTime);
    }
    
    private static AckEntry prepareAckEntry(InetSocketAddress socketAddress, byte[] dataBytes, Map<String, Object> data,
            long lastRefTime) {
        String key = AckEntry
                .getAckKey(socketAddress.getAddress().getHostAddress(), socketAddress.getPort(), lastRefTime);
        try {
            DatagramPacket packet = new DatagramPacket(dataBytes, dataBytes.length, socketAddress);
            AckEntry ackEntry = new AckEntry(key, packet);
            // we must store the key be fore send, otherwise there will be a chance the
            // ack returns before we put in
            ackEntry.setData(data);
            return ackEntry;
        } catch (Exception e) {
            Loggers.PUSH
                    .error("[NACOS-PUSH] failed to prepare data: {} to client: {}, error: {}", data, socketAddress, e);
        }
        return null;
    }
    
    public static String getPushCacheKey(String serviceName, String clientIP, String agent) {
        return serviceName + UtilsAndCommons.CACHE_KEY_SPLITTER + agent;
    }
    
    /**
     * Service changed.
     *
     * @param service service
     */
    public void serviceChanged(Service service) {
        this.applicationContext.publishEvent(new ServiceChangeEvent(this, service));
    }
    
    /**
     * Judge whether this agent is supported to push.
     *
     * @param agent agent information
     * @return true if agent can be pushed, otherwise false
     */
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
        } else if (ClientInfo.ClientType.CSHARP == clientInfo.type
                && clientInfo.version.compareTo(VersionUtil.parseVersion(switchDomain.getPushCSharpVersion())) >= 0) {
            return true;
        }
        
        return false;
    }
    
    public static List<AckEntry> getFailedPushes() {
        return new ArrayList<>(ackMap.values());
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
        return prepareHostsData(client.getDataSource().getData(client));
    }
    
    private static Map<String, Object> prepareHostsData(String dataContent) {
        Map<String, Object> result = new HashMap<String, Object>(2);
        result.put("type", "dom");
        result.put("data", dataContent);
        return result;
    }
    
    private static AckEntry udpPush(AckEntry ackEntry) {
        if (ackEntry == null) {
            Loggers.PUSH.error("[NACOS-PUSH] ackEntry is null.");
            return null;
        }
        
        if (ackEntry.getRetryTimes() > Constants.UDP_MAX_RETRY_TIMES) {
            Loggers.PUSH.warn("max re-push times reached, retry times {}, key: {}", ackEntry.getRetryTimes(),
                    ackEntry.getKey());
            ackMap.remove(ackEntry.getKey());
            udpSendTimeMap.remove(ackEntry.getKey());
            MetricsMonitor.incrementFailPush();
            return ackEntry;
        }
        
        try {
            if (!ackMap.containsKey(ackEntry.getKey())) {
                MetricsMonitor.incrementPush();
            }
            ackMap.put(ackEntry.getKey(), ackEntry);
            udpSendTimeMap.put(ackEntry.getKey(), System.currentTimeMillis());
            
            Loggers.PUSH.info("send udp packet: " + ackEntry.getKey());
            udpSocket.send(ackEntry.getOrigin());
            
            ackEntry.increaseRetryTime();
            
            GlobalExecutor.scheduleRetransmitter(new Retransmitter(ackEntry),
                    TimeUnit.NANOSECONDS.toMillis(Constants.ACK_TIMEOUT_NANOS), TimeUnit.MILLISECONDS);
            
            return ackEntry;
        } catch (Exception e) {
            Loggers.PUSH.error("[NACOS-PUSH] failed to push data: {} to client: {}, error: {}", ackEntry.getData(),
                    ackEntry.getOrigin().getAddress().getHostAddress(), e);
            ackMap.remove(ackEntry.getKey());
            udpSendTimeMap.remove(ackEntry.getKey());
            MetricsMonitor.incrementFailPush();
            
            return null;
        }
    }
    
    public static class Retransmitter implements Runnable {
        
        AckEntry ackEntry;
        
        public Retransmitter(AckEntry ackEntry) {
            this.ackEntry = ackEntry;
        }
        
        @Override
        public void run() {
            if (ackMap.containsKey(ackEntry.getKey())) {
                Loggers.PUSH.info("retry to push data, key: " + ackEntry.getKey());
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
                    AckPacket ackPacket = JacksonUtils.toObj(json, AckPacket.class);
                    
                    InetSocketAddress socketAddress = (InetSocketAddress) packet.getSocketAddress();
                    String ip = socketAddress.getAddress().getHostAddress();
                    int port = socketAddress.getPort();
                    
                    if (System.nanoTime() - ackPacket.lastRefTime > Constants.ACK_TIMEOUT_NANOS) {
                        Loggers.PUSH.warn("ack takes too long from {} ack json: {}", packet.getSocketAddress(), json);
                    }
                    
                    String ackKey = AckEntry.getAckKey(ip, port, ackPacket.lastRefTime);
                    AckEntry ackEntry = ackMap.remove(ackKey);
                    if (ackEntry == null) {
                        throw new IllegalStateException(
                                "unable to find ackEntry for key: " + ackKey + ", ack json: " + json);
                    }
                    
                    long pushCost = System.currentTimeMillis() - udpSendTimeMap.get(ackKey);
                    
                    Loggers.PUSH
                            .info("received ack: {} from: {}:{}, cost: {} ms, unacked: {}, total push: {}", json, ip,
                                    port, pushCost, ackMap.size(), MetricsMonitor.getTotalPushMonitor().get());
                    
                    MetricsMonitor.incrementPushCost(pushCost);
                    
                    udpSendTimeMap.remove(ackKey);
                    
                } catch (Throwable e) {
                    Loggers.PUSH.error("[NACOS-PUSH] error while receiving ack data", e);
                }
            }
        }
        
    }
    
}
