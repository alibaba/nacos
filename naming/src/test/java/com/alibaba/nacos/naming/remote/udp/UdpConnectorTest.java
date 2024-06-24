/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.remote.udp;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * {@link UdpConnector} unit tests.
 *
 * @author chenglu
 * @date 2021-09-15 19:52
 */
@ExtendWith(MockitoExtension.class)
class UdpConnectorTest {
    
    @InjectMocks
    private UdpConnector udpConnector;
    
    @Mock
    private ConcurrentMap<String, AckEntry> ackMap;
    
    @Mock
    private ConcurrentMap<String, PushCallBack> callbackMap;
    
    @Mock
    private DatagramSocket udpSocket;
    
    @BeforeAll
    static void setEnv() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        ReflectionTestUtils.setField(udpConnector, "ackMap", ackMap);
        ReflectionTestUtils.setField(udpConnector, "callbackMap", callbackMap);
        DatagramSocket oldSocket = (DatagramSocket) ReflectionTestUtils.getField(udpConnector, "udpSocket");
        ReflectionTestUtils.setField(udpConnector, "udpSocket", udpSocket);
        doAnswer(invocationOnMock -> {
            TimeUnit.SECONDS.sleep(3);
            return null;
        }).when(udpSocket).receive(any(DatagramPacket.class));
        oldSocket.close();
        TimeUnit.SECONDS.sleep(1);
    }
    
    @AfterEach
    void tearDown() throws InterruptedException {
        udpConnector.shutdown();
        TimeUnit.SECONDS.sleep(1);
    }
    
    @Test
    void testContainAck() {
        when(ackMap.containsKey("1111")).thenReturn(true);
        assertTrue(udpConnector.containAck("1111"));
    }
    
    @Test
    void testSendData() throws NacosException, IOException {
        when(udpSocket.isClosed()).thenReturn(false);
        AckEntry ackEntry = new AckEntry("A", new DatagramPacket(new byte[2], 2));
        udpConnector.sendData(ackEntry);
        Mockito.verify(udpSocket).send(ackEntry.getOrigin());
    }
    
    @Test
    void testSendDataWithCallback() throws IOException, InterruptedException {
        when(udpSocket.isClosed()).thenReturn(false);
        AckEntry ackEntry = new AckEntry("A", new DatagramPacket(new byte[2], 2));
        udpConnector.sendDataWithCallback(ackEntry, new PushCallBack() {
            @Override
            public long getTimeout() {
                return 0;
            }
            
            @Override
            public void onSuccess() {
            
            }
            
            @Override
            public void onFail(Throwable e) {
                fail(e.getMessage());
            }
        });
        Thread.sleep(100);
        Mockito.verify(udpSocket).send(ackEntry.getOrigin());
    }
}
