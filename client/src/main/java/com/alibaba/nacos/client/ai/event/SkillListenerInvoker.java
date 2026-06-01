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

import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.listener.NacosSkillEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos AI module skill listener invoker.
 *
 * @author nacos
 */
public class SkillListenerInvoker
    extends AbstractAiListenerInvoker<NacosSkillEvent, AbstractNacosSkillListener> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillListenerInvoker.class);
    
    public SkillListenerInvoker(AbstractNacosSkillListener listener) {
        super(listener);
    }
    
    @Override
    protected void logInvoke(NacosSkillEvent event) {
        LOGGER.info("Invoke event skillName: {} to Listener: {}", event.getSkillName(),
            listener.toString());
    }
}
