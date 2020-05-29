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
package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.IoUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.Charset;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * @author xuanyin
 */
public class PushReceiver implements Runnable {

    private ScheduledExecutorService executorService;

    private static final int UDP_MSS = 64 * 1024;

    private DatagramSocket udpSocket;

    private HostReactor hostReactor;

    public PushReceiver(HostReactor hostReactor) {
        try {
            this.hostReactor = hostReactor;
            udpSocket = new DatagramSocket();

            executorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setDaemon(true);
                    thread.setName("com.alibaba.nacos.naming.push.receiver");
                    return thread;
                }
            });

            executorService.execute(this);
        } catch (Exception e) {
            NAMING_LOGGER.error("[NA] init udp socket failed", e);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                // byte[] is initialized with 0 full filled by default
                byte[] buffer = new byte[UDP_MSS];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                udpSocket.receive(packet);

                String json = new String(IoUtils.tryDecompress(packet.getData()), "UTF-8").trim();
                NAMING_LOGGER.info("received push data: " + json + " from " + packet.getAddress().toString());

                PushPacket pushPacket = JacksonUtils.toObj(json, PushPacket.class);
                String ack;
                if ("dom".equals(pushPacket.type) || "service".equals(pushPacket.type)) {
                    hostReactor.processServiceJSON(pushPacket.data);

                    // send ack to server
                    ack = "{\"type\": \"push-ack\""
                        + ", \"lastRefTime\":\"" + pushPacket.lastRefTime
                        + "\", \"data\":" + "\"\"}";
                } else if ("dump".equals(pushPacket.type)) {
                    // dump data to server
                    ack = "{\"type\": \"dump-ack\""
                        + ", \"lastRefTime\": \"" + pushPacket.lastRefTime
                        + "\", \"data\":" + "\""
                        + StringUtils.escapeJavaScript(JacksonUtils.toJson(hostReactor.getServiceInfoMap()))
                        + "\"}";
                } else {
                    // do nothing send ack only
                    ack = "{\"type\": \"unknown-ack\""
                        + ", \"lastRefTime\":\"" + pushPacket.lastRefTime
                        + "\", \"data\":" + "\"\"}";
                }

                udpSocket.send(new DatagramPacket(ack.getBytes(Charset.forName("UTF-8")),
                    ack.getBytes(Charset.forName("UTF-8")).length, packet.getSocketAddress()));
            } catch (Exception e) {
                NAMING_LOGGER.error("[NA] error while receiving push data", e);
            }
        }
    }

    public static class PushPacket {
        public String type;
        public long lastRefTime;
        public String data;
    }

    public int getUDPPort() {
        return udpSocket.getLocalPort();
    }
}
