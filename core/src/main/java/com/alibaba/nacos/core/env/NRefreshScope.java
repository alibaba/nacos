/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.env;

import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NRefreshScope implements Subscribe<RefreshEvent>, Scope, DisposableBean {

    private final Map<String, Object> beanCache = new ConcurrentHashMap<>();

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        beanCache.computeIfAbsent(name, s -> objectFactory.getObject());
        return beanCache.get(name);
    }

    @Override
    public Object remove(String name) {
        return beanCache.remove(name);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {

    }

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return getClass().getCanonicalName();
    }

    @Override
    public void onEvent(RefreshEvent event) {
        beanCache.clear();
    }

    @Override
    public Class<? extends Event> subscribeType() {
        return RefreshEvent.class;
    }

    @Override
    public void destroy() throws Exception {
        beanCache.clear();
    }
}
