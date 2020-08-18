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

package com.alibaba.nacos.test.core;

import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.utils.NetUtils;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import io.rsocket.util.RSocketProxy;
import org.junit.Test;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.UUID;

/**
 * @author liuzunfei
 * @version $Id: RsocketClientTest.java, v 0.1 2020年08月07日 11:05 PM liuzunfei Exp $
 */
public class RsocketClientTest {
    
    
    @Test
    public void testConnection() throws Exception {
        String connectId = UUID.randomUUID().toString();
        ConnectionSetupRequest conconSetupRequest = new ConnectionSetupRequest(NetUtils.localIP(), "1.4.0",
                new HashMap<>());
        Payload setUpPayload = DefaultPayload.create(connectId);
        ;
        System.out.println("setUpPayload：" + setUpPayload.getDataUtf8());
        
        RSocket socketClient = RSocketConnector.create().setupPayload(setUpPayload).acceptor(new SocketAcceptor() {
            @Override
            public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
                
                RSocket rsocket = new RSocketProxy(sendingSocket) {
                    @Override
                    public Mono<Payload> requestResponse(Payload payload) {
                        System.out.println("收到服务端推送：" + payload.getDataUtf8());
                        return Mono.just(DefaultPayload.create("Push OK."));
                        
                    }
                };
                
                return Mono.just((RSocket) rsocket);
            }
        }).connect(TcpClientTransport.create("localhost", 9948)).block();
        
        System.out.println("socketClient:" + socketClient);
        for (int i = 0; i < 100; i++) {
            Mono<Payload> payloadMono = socketClient.requestResponse(DefaultPayload.create("helloserver:" + i));
            Payload block = payloadMono.block();
            System.out.println("Server response:" + block.getDataUtf8());
            Thread.sleep(2000L);
        }
        
        Thread.sleep(7000000L);
    }
    
    public static void main(String[] args) throws Exception {
        String connectId = UUID.randomUUID().toString();
        ConnectionSetupRequest conconSetupRequest = new ConnectionSetupRequest(NetUtils.localIP(), "1.4.0",
                new HashMap<>());
        Payload setUpPayload = DefaultPayload.create(connectId);
        ;
        System.out.println("setUpPayload2：" + setUpPayload.getDataUtf8());
        
        RSocket socketClient = RSocketConnector.create().setupPayload(setUpPayload).acceptor(new SocketAcceptor() {
            @Override
            public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
                
                RSocket rsocket = new RSocketProxy(sendingSocket) {
                    @Override
                    public Mono<Payload> requestResponse(Payload payload) {
                        System.out.println("收到服务端推送：" + payload.getDataUtf8());
                        return Mono.just(DefaultPayload.create("Push OK."));
                        
                    }
                };
                
                return Mono.just((RSocket) rsocket);
            }
        }).connect(TcpClientTransport.create("localhost", 9948)).block();
        
        System.out.println("socketClient:" + socketClient);
        for (int i = 0; i < 100; i++) {
            Mono<Payload> payloadMono = socketClient.requestResponse(DefaultPayload.create("helloserver:" + i));
            Payload block = payloadMono.block();
            System.out.println("Server response:" + block.getDataUtf8());
            Thread.sleep(2000L);
        }
        
        Thread.sleep(7000000L);
    }
    
}
