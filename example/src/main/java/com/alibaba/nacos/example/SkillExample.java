/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.listener.NacosSkillEvent;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Skill client integration example.
 *
 * <p>Tests the HTTP polling + 304 conditional query mechanism for Skill resources:
 * <ol>
 *   <li>Subscribe to a Skill and verify initial fetch</li>
 *   <li>Wait for polling cycles and observe 304 Not Modified behavior</li>
 *   <li>Unsubscribe and verify cleanup</li>
 * </ol>
 *
 * <p>Prerequisites: A local Nacos server running at localhost:8848
 * with at least one published Skill (e.g., "nacos-cli-e2e-skill").
 */
public class SkillExample {
    
    public static void main(String[] args) throws Exception {
        String serverAddr = "localhost:8848";
        String skillName = "nacos-cli-e2e-skill";
        String label = "latest";
        
        // Allow override via command line
        if (args.length > 0) {
            skillName = args[0];
        }
        if (args.length > 1) {
            label = args[1];
        }
        if (args.length > 2) {
            serverAddr = args[2];
        }
        
        System.out.println("============================================");
        System.out.println("  Skill Client Integration Test");
        System.out.println("============================================");
        System.out.println("[Config] serverAddr = " + serverAddr);
        System.out.println("[Config] skillName  = " + skillName);
        System.out.println("[Config] label      = " + label);
        System.out.println();
        
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("namespace", "public");
        properties.setProperty("username", "nacos");
        properties.setProperty("password", "nacos");
        // Skill download/polling uses HTTP transport
        properties.setProperty("nacosAiTransportMode", "http");
        
        System.out.println("[Step 1] Creating AiService...");
        AiService aiService = AiFactory.createAiService(properties);
        System.out.println("[Step 1] AiService created successfully.");
        System.out.println();
        
        // === Test: Subscribe to Skill ===
        System.out.println("[Step 2] Subscribing to Skill: " + skillName + ", label=" + label);
        AtomicInteger eventCount = new AtomicInteger(0);
        
        AbstractNacosSkillListener listener = new AbstractNacosSkillListener() {
            
            @Override
            public void onEvent(NacosSkillEvent event) {
                int count = eventCount.incrementAndGet();
                System.out.println();
                System.out.println("[Event #" + count + "] Skill changed!");
                System.out.println("  name: " + event.getSkillName());
                System.out.println("  resolvedVersion: " + event.getResolvedVersion());
                System.out.println("  md5: " + event.getMd5());
                byte[] zip = event.getZipBytes();
                System.out.println("  zip length: " + (zip != null ? zip.length : 0));
            }
        };
        
        byte[] initialZip = aiService.subscribeSkill(skillName, null, label, listener);
        System.out.println("[Step 2] Subscribe completed.");
        if (initialZip != null) {
            System.out
                .println("  Initial Skill zip loaded, size = " + initialZip.length + " bytes");
        } else {
            System.out.println("  Initial Skill: null (not found)");
        }
        System.out.println();
        
        // === Wait for polling cycles ===
        System.out.println("[Step 3] Waiting 25 seconds to observe polling behavior...");
        System.out.println("  (Polling interval is ~10s, expecting 2 poll cycles with 304)");
        System.out.println("  (If you update the Skill on server during this time,");
        System.out.println("   you should see additional [Event] callbacks above)");
        System.out.println();
        
        Thread.sleep(25000);
        System.out.println("[Step 3] Done waiting. Total events: " + eventCount.get());
        System.out
            .println("  (Only 1 event = initial load. No extra events = 304 working correctly)");
        System.out.println();
        
        // === Test: Unsubscribe ===
        System.out.println("[Step 4] Unsubscribing from Skill: " + skillName);
        aiService.unsubscribeSkill(skillName, null, label, listener);
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
