/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.remote.udp;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.push.v2.NoRequiredRetryException;
import com.alibaba.nacos.naming.constants.Constants;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Udp socket connector to send upd data and listen ack if necessary.
 *
 * @author xiweng.yy
 */
@Component
public class UdpConnector {
    
    private final ConcurrentMap<String, AckEntry> ackMap;
    
    private final ConcurrentMap<String, PushCallBack> callbackMap;
    
    private final DatagramSocket udpSocket;
    
    public UdpConnector() throws SocketException {
        this.ackMap = new ConcurrentHashMap<>();
        this.callbackMap = new ConcurrentHashMap<>();
        this.udpSocket = new DatagramSocket();
        GlobalExecutor.scheduleUdpReceiver(new UdpReceiver());
    }
    
    public boolean containAck(String ackId) {
        return ackMap.containsKey(ackId);
    }
    
    /**
     * Sync send data once.
     *
     * @param ackEntry ack entry
     * @throws NacosException nacos exception during sending
     */
    public void sendData(AckEntry ackEntry) throws NacosException {
        if (null == ackEntry) {
            return;
        }
        try {
            MetricsMonitor.incrementPush();
            doSend(ackEntry.getOrigin());
        } catch (IOException e) {
            MetricsMonitor.incrementFailPush();
            throw new NacosException(NacosException.SERVER_ERROR, "[NACOS-PUSH] push data with exception: ", e);
        }
    }
    
    /**
     * Send Data with {@link PushCallBack}.
     *
     * @param ackEntry     ack entry
     * @param pushCallBack push callback
     */
    public void sendDataWithCallback(AckEntry ackEntry, PushCallBack pushCallBack) {
        if (null == ackEntry) {
            return;
        }
        GlobalExecutor.scheduleUdpSender(new UdpAsyncSender(ackEntry, pushCallBack), 0L, TimeUnit.MILLISECONDS);
    }
    
    private void doSend(DatagramPacket packet) throws IOException {
        if (!udpSocket.isClosed()) {
            udpSocket.send(packet);
        }
    }
    
    private void callbackSuccess(String ackKey) {
        PushCallBack pushCallBack = callbackMap.remove(ackKey);
        if (null != pushCallBack) {
            pushCallBack.onSuccess();
        }
    }
    
    private void callbackFailed(String ackKey, Throwable exception) {
        PushCallBack pushCallBack = callbackMap.remove(ackKey);
        if (null != pushCallBack) {
            pushCallBack.onFail(exception);
        }
    }
    
    private class UdpAsyncSender implements Runnable {
        
        private final AckEntry ackEntry;
        
        private final PushCallBack callBack;
        
        public UdpAsyncSender(AckEntry ackEntry, PushCallBack callBack) {
            this.ackEntry = ackEntry;
            this.callBack = callBack;
        }
        
        @Override
        public void run() {
            try {
                callbackMap.put(ackEntry.getKey(), callBack);
                ackMap.put(ackEntry.getKey(), ackEntry);
                Loggers.PUSH.info("send udp packet: " + ackEntry.getKey());
                ackEntry.increaseRetryTime();
                doSend(ackEntry.getOrigin());
                GlobalExecutor.scheduleRetransmitter(new UdpRetrySender(ackEntry), Constants.ACK_TIMEOUT_NANOS,
                        TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                ackMap.remove(ackEntry.getKey());
                callbackMap.remove(ackEntry.getKey());
                callBack.onFail(e);
            }
        }
    }
    
    private class UdpRetrySender implements Runnable {
        
        private final AckEntry ackEntry;
        
        public UdpRetrySender(AckEntry ackEntry) {
            this.ackEntry = ackEntry;
        }
        
        @Override
        public void run() {
            // Received ack, no need to retry
            if (!containAck(ackEntry.getKey())) {
                return;
            }
            // Match max retry, push failed.
            if (ackEntry.getRetryTimes() > Constants.UDP_MAX_RETRY_TIMES) {
                Loggers.PUSH.warn("max re-push times reached, retry times {}, key: {}", ackEntry.getRetryTimes(),
                        ackEntry.getKey());
                ackMap.remove(ackEntry.getKey());
                callbackFailed(ackEntry.getKey(), new NoRequiredRetryException());
                return;
            }
            Loggers.PUSH.info("retry to push data, key: " + ackEntry.getKey());
            try {
                ackEntry.increaseRetryTime();
                doSend(ackEntry.getOrigin());
                GlobalExecutor.scheduleRetransmitter(this, Constants.ACK_TIMEOUT_NANOS, TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                callbackFailed(ackEntry.getKey(), e);
                ackMap.remove(ackEntry.getKey());
            }
        }
    }
    
    private class UdpReceiver implements Runnable {
        
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
                    callbackSuccess(ackKey);
                } catch (Throwable e) {
                    Loggers.PUSH.error("[NACOS-PUSH] error while receiving ack data", e);
                }
            }
        }
        
    }
}
