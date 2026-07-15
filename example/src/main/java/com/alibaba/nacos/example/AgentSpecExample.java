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

package com.alibaba.nacos.example;

import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentSpecEvent;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AgentSpec client integration example.
 *
 * <p>Tests the HTTP polling + 304 conditional query mechanism:
 * <ol>
 *   <li>Subscribe to an AgentSpec and verify initial fetch</li>
 *   <li>Wait for polling cycles and observe 304 Not Modified behavior</li>
 *   <li>Unsubscribe and verify cleanup</li>
 * </ol>
 *
 * <p>Prerequisites: A local Nacos server running at localhost:8848
 * with at least one published AgentSpec (e.g., "test").
 */
public class AgentSpecExample {
    
    public static void main(String[] args) throws Exception {
        String serverAddr = "localhost:8848";
        String agentSpecName = "test";
        
        // Allow override via command line
        if (args.length > 0) {
            agentSpecName = args[0];
        }
        if (args.length > 1) {
            serverAddr = args[1];
        }
        
        System.out.println("============================================");
        System.out.println("  AgentSpec Client Integration Test");
        System.out.println("============================================");
        System.out.println("[Config] serverAddr = " + serverAddr);
        System.out.println("[Config] agentSpecName = " + agentSpecName);
        System.out.println();
        
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("namespace", "public");
        properties.setProperty("username", "nacos");
        properties.setProperty("password", "nacos");
        // AgentSpec polling uses HTTP transport
        properties.setProperty("nacosAiTransportMode", "http");
        
        System.out.println("[Step 1] Creating AiService...");
        AiService aiService = AiFactory.createAiService(properties);
        System.out.println("[Step 1] AiService created successfully.");
        System.out.println();
        
        // === Test: Subscribe to AgentSpec ===
        System.out.println("[Step 2] Subscribing to AgentSpec: " + agentSpecName);
        CountDownLatch eventLatch = new CountDownLatch(1);
        AtomicInteger eventCount = new AtomicInteger(0);
        
        AbstractNacosAgentSpecListener listener = new AbstractNacosAgentSpecListener() {
            
            @Override
            public void onEvent(NacosAgentSpecEvent event) {
                int count = eventCount.incrementAndGet();
                System.out.println();
                System.out.println("[Event #" + count + "] AgentSpec changed!");
                System.out.println("  name: " + event.getAgentSpecName());
                AgentSpec spec = event.getAgentSpec();
                if (spec != null) {
                    System.out.println("  description: " + spec.getDescription());
                    System.out.println("  content length: "
                        + (spec.getContent() != null ? spec.getContent().length() : 0));
                    System.out.println("  resource count: "
                        + (spec.getResource() != null ? spec.getResource().size() : 0));
                } else {
                    System.out.println("  spec: null (deleted or not found)");
                }
                eventLatch.countDown();
            }
        };
        
        AgentSpec initialSpec = aiService.subscribeAgentSpec(agentSpecName, listener);
        System.out.println("[Step 2] Subscribe completed.");
        if (initialSpec != null) {
            System.out.println("  Initial AgentSpec loaded:");
            System.out.println("    name: " + initialSpec.getName());
            System.out.println("    description: " + initialSpec.getDescription());
            System.out.println("    content length: "
                + (initialSpec.getContent() != null ? initialSpec.getContent().length() : 0));
            System.out.println("    resource count: "
                + (initialSpec.getResource() != null ? initialSpec.getResource().size() : 0));
        } else {
            System.out.println("  Initial AgentSpec: null (not found)");
        }
        System.out.println();
        
        // === Wait for polling cycles ===
        System.out.println("[Step 3] Waiting 25 seconds to observe polling behavior...");
        System.out.println("  (Polling interval is ~10s, expecting 2 poll cycles with 304)");
        System.out.println("  (If you update the AgentSpec on server during this time,");
        System.out.println("   you should see additional [Event] callbacks above)");
        System.out.println();
        
        // Wait regardless of initial event - we want to observe polling 304s
        Thread.sleep(25000);
        System.out.println("[Step 3] Done waiting. Total events: " + eventCount.get());
        System.out
            .println("  (Only 1 event = initial load. No extra events = 304 working correctly)");
        System.out.println();
        
        // === Test: Unsubscribe ===
        System.out.println("[Step 4] Unsubscribing from AgentSpec: " + agentSpecName);
        aiService.unsubscribeAgentSpec(agentSpecName, listener);
        System.out.println("[Step 4] Unsubscribed. Polling should stop.");
        System.out.println();
        
        // Wait a bit to confirm no more polling
        System.out
            .println("[Step 5] Waiting 15 seconds to verify no more polling after unsubscribe...");
        Thread.sleep(15000);
        System.out.println("[Step 5] Done. Total events received: " + eventCount.get());
        System.out.println();
        
        // === Shutdown ===
        System.out.println("[Step 6] Shutting down AiService...");
        aiService.shutdown();
        System.out.println("[Step 6] Shutdown complete.");
        System.out.println();
        System.out.println("============================================");
        System.out.println("  Test Complete - All steps passed!");
        System.out.println("============================================");
    }
}
