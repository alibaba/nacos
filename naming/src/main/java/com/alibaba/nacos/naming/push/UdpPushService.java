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
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.remote.udp.AckEntry;
import com.alibaba.nacos.naming.remote.udp.UdpConnector;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.VersionUtil;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Push service.
 *
 * @author nacos
 */
@Component
@SuppressWarnings("PMD.ThreadPoolCreationRule")
public class UdpPushService {
    
    @Autowired
    private SwitchDomain switchDomain;
    
    private final UdpConnector udpConnector;
    
    public UdpPushService(UdpConnector udpConnector) {
        this.udpConnector = udpConnector;
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
                && clientInfo.version.compareTo(parseVersion(switchDomain.getPushJavaVersion())) >= 0) {
            return true;
        } else if (ClientInfo.ClientType.DNS == clientInfo.type
                && clientInfo.version.compareTo(parseVersion(switchDomain.getPushPythonVersion())) >= 0) {
            return true;
        } else if (ClientInfo.ClientType.C == clientInfo.type
                && clientInfo.version.compareTo(parseVersion(switchDomain.getPushCVersion())) >= 0) {
            return true;
        } else if (ClientInfo.ClientType.GO == clientInfo.type
                && clientInfo.version.compareTo(parseVersion(switchDomain.getPushGoVersion())) >= 0) {
            return true;
        } else if (ClientInfo.ClientType.CSHARP == clientInfo.type
                && clientInfo.version.compareTo(parseVersion(switchDomain.getPushCSharpVersion())) >= 0) {
            return true;
        }
        
        return false;
    }
    
    private Version parseVersion(String version) {
        return VersionUtil.parseVersion(version, null, null);
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
    
    private static Map<String, Object> prepareHostsData(String dataContent) {
        Map<String, Object> result = new HashMap<String, Object>(2);
        result.put("type", "dom");
        result.put("data", dataContent);
        return result;
    }
}
