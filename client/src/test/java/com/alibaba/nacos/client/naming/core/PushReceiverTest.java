/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PushReceiverTest {
    
    @Test
    public void testTestRunDomAndService() throws InterruptedException, IOException {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        final PushReceiver pushReceiver = new PushReceiver(holder);
        final ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                pushReceiver.run();
            }
        });
        TimeUnit.MILLISECONDS.sleep(10);
        
        PushReceiver.PushPacket pack1 = new PushReceiver.PushPacket();
        pack1.type = "dom";
        pack1.data = "pack1";
        pack1.lastRefTime = 1;
        final String res1 = udpClientRun(pack1, pushReceiver);
        Assert.assertEquals("{\"type\": \"push-ack\", \"lastRefTime\":\"1\", \"data\":\"\"}", res1);
        verify(holder, times(1)).processServiceInfo(pack1.data);
        
        PushReceiver.PushPacket pack2 = new PushReceiver.PushPacket();
        pack2.type = "service";
        pack2.data = "pack2";
        pack2.lastRefTime = 2;
        final String res2 = udpClientRun(pack2, pushReceiver);
        Assert.assertEquals("{\"type\": \"push-ack\", \"lastRefTime\":\"2\", \"data\":\"\"}", res2);
        verify(holder, times(1)).processServiceInfo(pack2.data);
        
    }
    
    private String udpClientRun(PushReceiver.PushPacket pack, PushReceiver pushReceiver) throws IOException {
        final int udpPort = pushReceiver.getUdpPort();
        String json = JacksonUtils.toJson(pack);
        final byte[] bytes = IoUtils.tryCompress(json, "UTF-8");
        final DatagramSocket datagramSocket = new DatagramSocket();
        datagramSocket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("localhost"), udpPort));
        byte[] buffer = new byte[20480];
        final DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
        datagramSocket.receive(datagramPacket);
        final byte[] data = datagramPacket.getData();
        String res = new String(data, StandardCharsets.UTF_8);
        return res.trim();
    }
    
    @Test
    public void testTestRunWithDump() throws InterruptedException, IOException {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        final PushReceiver pushReceiver = new PushReceiver(holder);
        final ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                pushReceiver.run();
            }
        });
        TimeUnit.MILLISECONDS.sleep(10);
        
        PushReceiver.PushPacket pack1 = new PushReceiver.PushPacket();
        pack1.type = "dump";
        pack1.data = "pack1";
        pack1.lastRefTime = 1;
        final String res1 = udpClientRun(pack1, pushReceiver);
        Assert.assertEquals("{\"type\": \"dump-ack\", \"lastRefTime\": \"1\", \"data\":\"{}\"}", res1);
        verify(holder, times(1)).getServiceInfoMap();
        
    }
    
    @Test
    public void testTestRunWithUnknown() throws InterruptedException, IOException {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        final PushReceiver pushReceiver = new PushReceiver(holder);
        final ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                pushReceiver.run();
            }
        });
        TimeUnit.MILLISECONDS.sleep(10);
        
        PushReceiver.PushPacket pack1 = new PushReceiver.PushPacket();
        pack1.type = "unknown";
        pack1.data = "pack1";
        pack1.lastRefTime = 1;
        final String res1 = udpClientRun(pack1, pushReceiver);
        Assert.assertEquals("{\"type\": \"unknown-ack\", \"lastRefTime\":\"1\", \"data\":\"\"}", res1);
    }
    
    @Test
    public void testShutdown() throws NacosException, NoSuchFieldException, IllegalAccessException {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        final PushReceiver pushReceiver = new PushReceiver(holder);
        
        pushReceiver.shutdown();
        
        final Field closed = PushReceiver.class.getDeclaredField("closed");
        closed.setAccessible(true);
        final boolean o = (boolean) closed.get(pushReceiver);
        Assert.assertTrue(o);
        
    }
    
    @Test
    public void testGetUdpPort() {
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        final PushReceiver pushReceiver = new PushReceiver(holder);
        final int udpPort = pushReceiver.getUdpPort();
        System.out.println("udpPort = " + udpPort);
        Assert.assertTrue(udpPort > 0);
    }
}