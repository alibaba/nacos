/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.selector;

import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.NamingContext;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.naming.listener.NamingChangeEvent;
import com.alibaba.nacos.client.selector.SelectorWrapper;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Naming selector wrapper.
 *
 * @author lideyou
 */
public class NamingSelectorWrapper extends SelectorWrapper<NamingContext, NamingEvent> {

    private NamingEvent cachedEvent;

    public NamingSelectorWrapper(NamingSelector selector, EventListener listener) {
        super(selector, new NamingListenerInvoker(listener));
    }

    @Override
    public boolean isSelectable(NamingContext context) {
        return context != null;
    }

    @Override
    public boolean isCallable(NamingEvent event) {
        boolean isChanged;
        if (event instanceof NamingChangeEvent) {
            NamingChangeEvent changeEvent = (NamingChangeEvent) event;
            isChanged = changeEvent.isAdded()
                    || changeEvent.isRemoved()
                    || changeEvent.isModified();
        } else {
            isChanged = isChanged(event);
        }
        cachedEvent = event;
        return isChanged;
    }

    private boolean isChanged(NamingEvent namingEvent) {
        if (null == namingEvent) {
            return false;
        }

        if (null == cachedEvent) {
            return true;
        }

        int newSize = namingEvent.getInstances().size();
        int oldSize = cachedEvent.getInstances().size();

        if (newSize != oldSize) {
            return true;
        }

        Map<String, Instance> oldInsMap = new HashMap<>(oldSize);
        for (Instance ins : cachedEvent.getInstances()) {
            oldInsMap.put(ins.toInetAddr(), ins);
        }

        Map<String, Instance> newInsMap = new HashMap<>(newSize);
        for (Instance ins : namingEvent.getInstances()) {
            newInsMap.put(ins.toInetAddr(), ins);
        }

        for (Map.Entry<String, Instance> entry : newInsMap.entrySet()) {
            String key = entry.getKey();
            Instance ins1 = entry.getValue();
            Instance ins2 = oldInsMap.get(key);
            if (null == ins2 || !StringUtils.equals(ins1.toString(), ins2.toString())) {
                return true;
            }
        }
        return false;
    }
}
