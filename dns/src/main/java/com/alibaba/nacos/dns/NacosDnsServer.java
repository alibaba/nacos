/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.xbill.DNS.Message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nacos DNS Server that serves DNS queries using Nacos naming service as backend.
 *
 * <p>Supports:
 * <ul>
 *   <li>Standard A record query</li>
 *   <li>UDP and TCP transport</li>
 *   <li>Healthy instance filtering</li>
 * </ul>
 *
 * @author Nacos
 */
@Component
public class NacosDnsServer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosDnsServer.class);
    
    private final NacosDnsProperties properties;
    private final NacosDnsQueryHandler queryHandler;
    
    private ExecutorService executor;
    private DatagramSocket udpSocket;
    private ServerSocket tcpSocket;
    private volatile boolean running = false;
    
    private static final int MAX_DNS_PACKET_SIZE = 512;
    private static final int THREAD_POOL_SIZE = 10;
    
    public NacosDnsServer(NacosDnsProperties properties,
            NacosDnsQueryHandler queryHandler) {
        this.properties = properties;
        this.queryHandler = queryHandler;
    }
    
    /**
     * Start DNS server when application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isEnabled()) {
            LOGGER.info("Nacos DNS server is disabled.");
            return;
        }
        
        executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE, r -> {
            Thread t = new Thread(r, "nacos-dns-server");
            t.setDaemon(true);
            return t;
        });
        
        // Start UDP listener
        executor.submit(this::startUdpListener);
        
        // Start TCP listener
        executor.submit(this::startTcpListener);
        
        running = true;
        LOGGER.info("Nacos DNS server started on port {}, domain suffix: {}",
                properties.getPort(), properties.getDomainSuffix());
    }
    
    /**
     * Stop DNS server.
     */
    public void stop() {
        running = false;
        if (udpSocket != null) {
            udpSocket.close();
        }
        if (tcpSocket != null) {
            try {
                tcpSocket.close();
            } catch (IOException e) {
                LOGGER.warn("Error closing TCP socket", e);
            }
        }
        if (executor != null) {
            executor.shutdown();
        }
        LOGGER.info("Nacos DNS server stopped.");
    }
    
    /**
     * Start UDP listener.
     */
    private void startUdpListener() {
        try {
            udpSocket = new DatagramSocket(properties.getPort());
            LOGGER.info("DNS UDP server listening on port {}", properties.getPort());
            
            byte[] buf = new byte[MAX_DNS_PACKET_SIZE];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                udpSocket.receive(packet);
                
                executor.submit(() -> {
                    try {
                        handleUdpPacket(packet);
                    } catch (Exception e) {
                        LOGGER.error("Error handling UDP DNS query", e);
                    }
                });
            }
        } catch (IOException e) {
            LOGGER.error("Failed to start UDP DNS server on port {}", properties.getPort(), e);
        }
    }
    
    /**
     * Handle a UDP DNS packet.
     */
    private void handleUdpPacket(DatagramPacket packet) throws IOException {
        byte[] data = packet.getData();
        int length = packet.getLength();
        
        Message query = new Message(data);
        Message response = queryHandler.handleQuery(query);
        
        byte[] responseData = response.toWire();
        DatagramPacket responsePacket = new DatagramPacket(
                responseData, responseData.length,
                packet.getAddress(), packet.getPort());
        udpSocket.send(responsePacket);
    }
    
    /**
     * Start TCP listener.
     */
    private void startTcpListener() {
        try {
            tcpSocket = new ServerSocket(properties.getPort());
            LOGGER.info("DNS TCP server listening on port {}", properties.getPort());
            
            while (running) {
                Socket clientSocket = tcpSocket.accept();
                executor.submit(() -> {
                    try {
                        handleTcpConnection(clientSocket);
                    } catch (Exception e) {
                        LOGGER.error("Error handling TCP DNS query", e);
                    } finally {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                });
            }
        } catch (IOException e) {
            LOGGER.error("Failed to start TCP DNS server on port {}", properties.getPort(), e);
        }
    }
    
    /**
     * Handle a TCP DNS connection.
     */
    private void handleTcpConnection(Socket clientSocket) throws IOException {
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
        
        while (true) {
            // TCP DNS: first 2 bytes are length
            int length = in.readUnsignedShort();
            if (length < 0) {
                break;
            }
            byte[] queryData = new byte[length];
            in.readFully(queryData);
            
            Message query = new Message(queryData);
            Message response = queryHandler.handleQuery(query);
            
            byte[] responseData = response.toWire();
            out.writeShort(responseData.length);
            out.write(responseData);
            out.flush();
        }
    }
    
    public boolean isRunning() {
        return running;
    }
}
