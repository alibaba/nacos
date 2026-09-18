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
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Section;

import jakarta.annotation.PreDestroy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <p>Hardened for production:
 * <ul>
 *   <li>UDP response is capped at 512 bytes (truncation bit set when overflow)</li>
 *   <li>Per-packet buffer copies to avoid receive-buffer race</li>
 *   <li>TCP read timeout and connection limit to prevent resource exhaustion</li>
 *   <li>Listener threads separated from worker pool</li>
 * </ul>
 *
 * @author Nacos
 */
@Component
public class NacosDnsServer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosDnsServer.class);
    
    /** Standard DNS UDP payload limit without EDNS. */
    private static final int UDP_MAX_RESPONSE_SIZE = 512;
    
    /** Receive buffer large enough for EDNS-sized UDP queries. */
    private static final int UDP_RECEIVE_BUFFER_SIZE = 4096;
    
    /** TCP read timeout in milliseconds. */
    private static final int TCP_SOCKET_TIMEOUT_MS = 5000;
    
    /** Maximum concurrent TCP connections. */
    private static final int MAX_TCP_CONNECTIONS = 64;
    
    /** UDP worker thread pool size for quick query processing. */
    private static final int UDP_WORKER_THREADS = 16;
    
    /** TCP worker thread pool size (separate to prevent slow TCP from blocking UDP). */
    private static final int TCP_WORKER_THREADS = 8;
    
    private final NacosDnsProperties properties;
    private final NacosDnsQueryHandler queryHandler;
    
    /** Dedicated listener threads (non-daemon, short-lived). */
    private Thread udpListenerThread;
    private Thread tcpListenerThread;
    
    /** Separate worker pools: UDP queries and TCP connections don't starve each other. */
    private ExecutorService udpWorkerPool;
    private ExecutorService tcpWorkerPool;
    
    private DatagramSocket udpSocket;
    private ServerSocket tcpSocket;
    private volatile boolean running = false;
    
    /** Track active TCP connections to enforce limit. */
    private final AtomicInteger activeTcpConnections = new AtomicInteger(0);
    
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
        
        udpWorkerPool = new ThreadPoolExecutor(
            UDP_WORKER_THREADS, UDP_WORKER_THREADS,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1024),
            r -> {
                Thread t = new Thread(r, "nacos-dns-udp-worker");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
        
        tcpWorkerPool = new ThreadPoolExecutor(
            TCP_WORKER_THREADS, TCP_WORKER_THREADS,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(256),
            r -> {
                Thread t = new Thread(r, "nacos-dns-tcp-worker");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
        
        // Bind UDP first to get the actual port (supports port=0 for ephemeral)
        try {
            udpSocket = new DatagramSocket(properties.getPort());
        } catch (IOException e) {
            LOGGER.error("Failed to bind UDP socket on port {}", properties.getPort(), e);
            return;
        }
        int actualPort = udpSocket.getLocalPort();
        
        // Bind TCP to the same port
        try {
            tcpSocket = new ServerSocket(actualPort);
        } catch (IOException e) {
            LOGGER.error("Failed to bind TCP socket on port {}", actualPort, e);
            udpSocket.close();
            udpWorkerPool.shutdownNow();
            tcpWorkerPool.shutdownNow();
            return;
        }
        
        running = true;
        
        udpListenerThread = new Thread(this::startUdpListenerLoop, "nacos-dns-udp-listener");
        udpListenerThread.setDaemon(true);
        udpListenerThread.start();
        
        tcpListenerThread = new Thread(this::startTcpListenerLoop, "nacos-dns-tcp-listener");
        tcpListenerThread.setDaemon(true);
        tcpListenerThread.start();
        
        LOGGER.info("Nacos DNS server started on port {}, domain suffix: {}",
            actualPort, properties.getDomainSuffix());
    }
    
    /**
     * Stop DNS server.
     */
    @PreDestroy
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
        if (udpWorkerPool != null) {
            udpWorkerPool.shutdown();
            try {
                udpWorkerPool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (tcpWorkerPool != null) {
            tcpWorkerPool.shutdown();
            try {
                tcpWorkerPool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("Nacos DNS server stopped.");
    }
    
    /**
     * Start UDP listener loop (socket already bound in start()).
     */
    private void startUdpListenerLoop() {
        try {
            LOGGER.info("DNS UDP server listening on port {}", udpSocket.getLocalPort());
            
            while (running) {
                byte[] recvBuf = new byte[UDP_RECEIVE_BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                udpSocket.receive(packet);
                
                // Defensive copy: extract only received bytes to avoid buffer race
                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();
                int dataLen = packet.getLength();
                byte[] queryData = new byte[dataLen];
                System.arraycopy(packet.getData(), packet.getOffset(), queryData, 0, dataLen);
                
                try {
                    udpWorkerPool.submit(() -> handleUdpQuery(queryData, clientAddr, clientPort));
                } catch (Exception e) {
                    LOGGER.warn("Dropping UDP query, worker queue full", e);
                }
            }
        } catch (IOException e) {
            if (running) {
                LOGGER.error("UDP DNS listener error", e);
            }
        }
    }
    
    /**
     * Handle a single UDP DNS query.
     */
    private void handleUdpQuery(byte[] queryData, InetAddress clientAddr, int clientPort) {
        try {
            Message query = new Message(queryData);
            Message response = queryHandler.handleQuery(query);
            
            byte[] responseData = response.toWire();
            // Truncate if response exceeds standard UDP size to prevent amplification
            if (responseData.length > UDP_MAX_RESPONSE_SIZE) {
                response.getHeader().setFlag(Flags.TC);
                responseData = response.toWire();
                if (responseData.length > UDP_MAX_RESPONSE_SIZE) {
                    // Fallback: send only header + question (still valid truncated response)
                    responseData = buildTruncatedResponse(query);
                }
            }
            
            synchronized (udpSocket) {
                udpSocket.send(
                    new DatagramPacket(responseData, responseData.length, clientAddr, clientPort));
            }
        } catch (Exception e) {
            LOGGER.debug("Error handling UDP DNS query from {}:{}", clientAddr, clientPort, e);
        }
    }
    
    /**
     * Build a minimal truncated response (header + question only).
     */
    private byte[] buildTruncatedResponse(Message query) throws IOException {
        Message truncated = new Message(query.getHeader().getID());
        truncated.getHeader().setFlag(Flags.QR);
        truncated.getHeader().setFlag(Flags.TC);
        truncated.getHeader().setFlag(Flags.RA);
        truncated.addRecord(query.getQuestion(), Section.QUESTION);
        return truncated.toWire();
    }
    
    /**
     * Start TCP listener loop (socket already bound in start()).
     */
    private void startTcpListenerLoop() {
        try {
            LOGGER.info("DNS TCP server listening on port {}", tcpSocket.getLocalPort());
            
            while (running) {
                Socket clientSocket = tcpSocket.accept();
                
                // Enforce connection limit
                if (activeTcpConnections.get() >= MAX_TCP_CONNECTIONS) {
                    LOGGER.warn("Too many TCP connections ({}), rejecting {}",
                        activeTcpConnections.get(), clientSocket.getRemoteSocketAddress());
                    clientSocket.close();
                    continue;
                }
                
                activeTcpConnections.incrementAndGet();
                try {
                    tcpWorkerPool.submit(() -> {
                        try {
                            handleTcpConnection(clientSocket);
                        } catch (Exception e) {
                            LOGGER.debug("Error handling TCP DNS query from {}",
                                clientSocket.getRemoteSocketAddress(), e);
                        } finally {
                            activeTcpConnections.decrementAndGet();
                            try {
                                clientSocket.close();
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                    });
                } catch (Exception e) {
                    activeTcpConnections.decrementAndGet();
                    clientSocket.close();
                    LOGGER.warn("Dropping TCP connection, worker queue full", e);
                }
            }
        } catch (IOException e) {
            if (running) {
                LOGGER.error("Failed to start TCP DNS server on port {}", tcpSocket.getLocalPort(),
                    e);
            }
        }
    }
    
    /**
     * Handle a TCP DNS connection.
     *
     * <p>Enforces an absolute receive deadline covering both the 2-byte length
     * prefix and the complete message body, per RFC 7766 §6.2.3. The idle
     * deadline is reset only after a complete DNS message has been received.
     */
    private void handleTcpConnection(Socket clientSocket) throws IOException {
        clientSocket.setTcpNoDelay(true);
        
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
        
        long deadline = System.currentTimeMillis() + TCP_SOCKET_TIMEOUT_MS;
        
        while (running) {
            try {
                // Read 2-byte length prefix
                int b0 = readByteWithDeadline(in, clientSocket, deadline);
                if (b0 < 0) {
                    break;
                }
                int b1 = readByteWithDeadline(in, clientSocket, deadline);
                if (b1 < 0) {
                    break;
                }
                
                int length = (b0 << 8) | b1;
                if (length <= 0 || length > UDP_RECEIVE_BUFFER_SIZE) {
                    break;
                }
                
                // Read message body within remaining deadline
                byte[] queryData = new byte[length];
                int totalRead = 0;
                while (totalRead < length) {
                    if (checkDeadline(clientSocket, deadline) < 0) {
                        break;
                    }
                    int n = in.read(queryData, totalRead, length - totalRead);
                    if (n < 0) {
                        break;
                    }
                    totalRead += n;
                }
                if (totalRead < length) {
                    break;
                }
                
                Message query = new Message(queryData);
                Message response = queryHandler.handleQuery(query);
                
                byte[] responseData = response.toWire();
                synchronized (out) {
                    out.writeShort(responseData.length);
                    out.write(responseData);
                    out.flush();
                }
                
                // Reset idle deadline only after a complete message (RFC 7766)
                deadline = System.currentTimeMillis() + TCP_SOCKET_TIMEOUT_MS;
            } catch (java.net.SocketTimeoutException e) {
                break;
            }
        }
    }
    
    /**
     * Check remaining time until deadline and configure socket timeout.
     *
     * @return remaining milliseconds (>0), or -1 if deadline expired
     */
    private long checkDeadline(Socket socket, long deadline) throws SocketException {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            return -1;
        }
        socket.setSoTimeout((int) Math.min(remaining, Integer.MAX_VALUE));
        return remaining;
    }
    
    /**
     * Read one byte, respecting the absolute deadline.
     *
     * @return the byte read (0-255), or -1 on EOF/deadline expiry
     */
    private int readByteWithDeadline(DataInputStream in, Socket socket, long deadline)
        throws IOException {
        if (checkDeadline(socket, deadline) < 0) {
            return -1;
        }
        int b = in.read();
        if (b < 0) {
            return -1;
        }
        return b;
    }
    
    public boolean isRunning() {
        return running;
    }
}
