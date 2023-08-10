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
import com.alibaba.nacos.client.selector.AbstractSelectorWrapper;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Naming selector wrapper.
 *
 * @author lideyou
 */
public class NamingSelectorWrapper extends AbstractSelectorWrapper<NamingContext, NamingEvent> {

    private Map<String, String> cachedInsMap;

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
            if (isChanged) {
                refreshCachedInsMap(event.getInstances());
            }
        }
        return isChanged;
    }

    private boolean isChanged(NamingEvent namingEvent) {
        if (namingEvent == null) {
            return false;
        }

        if (cachedInsMap == null) {
            return true;
        }

        int newSize = namingEvent.getInstances().size();
        int oldSize = cachedInsMap.size();

        if (newSize != oldSize) {
            return true;
        }

        Map<String, Instance> newInsMap = new HashMap<>(newSize);
        for (Instance ins : namingEvent.getInstances()) {
            newInsMap.put(ins.toInetAddr(), ins);
        }

        for (Map.Entry<String, Instance> entry : newInsMap.entrySet()) {
            String key = entry.getKey();
            Instance ins1 = entry.getValue();
            String ins2 = cachedInsMap.get(key);

            if (ins2 == null || !StringUtils.equals(ins1.toString(), ins2)) {
                return true;
            }
        }
        return false;
    }

    private void refreshCachedInsMap(List<Instance> instances) {
        if (cachedInsMap == null) {
            cachedInsMap = new HashMap<>(instances.size());
        }
        cachedInsMap.clear();
        for (Instance ins : instances) {
            cachedInsMap.put(ins.toInetAddr(), ins.toString());
        }
    }
}
