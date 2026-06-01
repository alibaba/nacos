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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChangeNotifierPromptTest {
    
    @Test
    void promptEventShouldDispatchByCacheKeyOnly() {
        AiChangeNotifier notifier = new AiChangeNotifier();
        AtomicBoolean v1Called = new AtomicBoolean(false);
        AtomicBoolean v2Called = new AtomicBoolean(false);
        notifier.registerListener("p1", "1.0.0", null,
            new PromptListenerInvoker(newPromptListener(v1Called)));
        notifier.registerListener("p1", "2.0.0", null,
            new PromptListenerInvoker(newPromptListener(v2Called)));
        
        Prompt prompt = new Prompt();
        prompt.setPromptKey("p1");
        notifier.onEvent(new PromptChangedEvent("p1", "p1::version:1.0.0", prompt));
        
        assertTrue(v1Called.get());
        assertFalse(v2Called.get());
    }
    
    @Test
    void deregisterShouldStopDispatchForTargetKey() {
        AiChangeNotifier notifier = new AiChangeNotifier();
        AtomicBoolean called = new AtomicBoolean(false);
        PromptListenerInvoker invoker = new PromptListenerInvoker(newPromptListener(called));
        notifier.registerListener("p1", null, "prod", invoker);
        notifier.deregisterListener("p1", null, "prod", invoker);
        
        notifier.onEvent(new PromptChangedEvent("p1", "p1::label:prod", new Prompt()));
        assertFalse(called.get());
    }
    
    @Test
    void isPromptSubscribedShouldMatchTripleKey() {
        AiChangeNotifier notifier = new AiChangeNotifier();
        PromptListenerInvoker invoker =
            new PromptListenerInvoker(newPromptListener(new AtomicBoolean(false)));
        notifier.registerListener("p1", null, "prod", invoker);
        
        assertTrue(notifier.isPromptSubscribed("p1", null, "prod"));
        assertFalse(notifier.isPromptSubscribed("p1", "1.0.0", null));
    }
    
    private AbstractNacosPromptListener newPromptListener(AtomicBoolean mark) {
        return new AbstractNacosPromptListener() {
            
            @Override
            public void onEvent(NacosPromptEvent event) {
                mark.set(true);
            }
        };
    }
}
