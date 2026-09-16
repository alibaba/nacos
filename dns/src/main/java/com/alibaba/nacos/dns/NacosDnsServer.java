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
import java.util.concurrent.Executors;
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

    /** Worker thread pool size for query processing. */
    private static final int WORKER_THREADS = 16;

    private final NacosDnsProperties properties;
    private final NacosDnsQueryHandler queryHandler;

    /** Dedicated listener threads (non-daemon, short-lived). */
    private Thread udpListenerThread;
    private Thread tcpListenerThread;

    /** Worker pool for query processing (bounded queue). */
    private ExecutorService workerPool;

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

        workerPool = new ThreadPoolExecutor(
                WORKER_THREADS, WORKER_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1024),
                r -> {
                    Thread t = new Thread(r, "nacos-dns-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());

        udpListenerThread = new Thread(this::startUdpListener, "nacos-dns-udp-listener");
        udpListenerThread.setDaemon(true);
        udpListenerThread.start();

        tcpListenerThread = new Thread(this::startTcpListener, "nacos-dns-tcp-listener");
        tcpListenerThread.setDaemon(true);
        tcpListenerThread.start();

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
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                workerPool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("Nacos DNS server stopped.");
    }

    /**
     * Start UDP listener loop.
     */
    private void startUdpListener() {
        try {
            udpSocket = new DatagramSocket(properties.getPort());
            LOGGER.info("DNS UDP server listening on port {}", properties.getPort());

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
                    workerPool.submit(() -> handleUdpQuery(queryData, clientAddr, clientPort));
                } catch (Exception e) {
                    LOGGER.warn("Dropping UDP query, worker queue full", e);
                }
            }
        } catch (SocketException e) {
            if (running) {
                LOGGER.error("Failed to start UDP DNS server on port {}", properties.getPort(), e);
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
                udpSocket.send(new DatagramPacket(responseData, responseData.length, clientAddr, clientPort));
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
        truncated.addRecord(query.getQuestion(), org.xbill.DNS.Section.QUESTION);
        return truncated.toWire();
    }

    /**
     * Start TCP listener loop.
     */
    private void startTcpListener() {
        try {
            tcpSocket = new ServerSocket(properties.getPort());
            LOGGER.info("DNS TCP server listening on port {}", properties.getPort());

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
                    workerPool.submit(() -> {
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
                LOGGER.error("Failed to start TCP DNS server on port {}", properties.getPort(), e);
            }
        }
    }

    /**
     * Handle a TCP DNS connection.
     */
    private void handleTcpConnection(Socket clientSocket) throws IOException {
        clientSocket.setSoTimeout(TCP_SOCKET_TIMEOUT_MS);
        clientSocket.setTcpNoDelay(true);

        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

        while (running) {
            try {
                // TCP DNS: first 2 bytes are message length
                int length = in.readUnsignedShort();
                if (length < 0 || length > UDP_RECEIVE_BUFFER_SIZE) {
                    break;
                }
                byte[] queryData = new byte[length];
                in.readFully(queryData);

                Message query = new Message(queryData);
                Message response = queryHandler.handleQuery(query);

                byte[] responseData = response.toWire();
                synchronized (out) {
                    out.writeShort(responseData.length);
                    out.write(responseData);
                    out.flush();
                }
            } catch (java.net.SocketTimeoutException e) {
                break;
            }
        }
    }

    public boolean isRunning() {
        return running;
    }
}
