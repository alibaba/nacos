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
package com.alibaba.nacos.common.lifecycle;

import com.alibaba.nacos.common.utils.ShutdownUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class to manage every instances' life cycle which register into it.
 * @author zongtanghu
 *
 */
public final class ResourceLifeCycleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceLifeCycleManager.class);

    /**
     * <Object instance...>
     */
    private List<AbstractLifeCycle> lifeCycleResources;

    /**
     * Map<Object instance, Object>
     */
    private Map<AbstractLifeCycle, Object> lockers = new ConcurrentHashMap<AbstractLifeCycle, Object>(8);

    private static final ResourceLifeCycleManager INSTANCE = new ResourceLifeCycleManager();

    private static final AtomicBoolean CLOSED = new AtomicBoolean(false);

    static {
        INSTANCE.init();
        ShutdownUtils.addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                LOGGER.warn("[LifeCycleManager] Start destroying Every Instance");
                shutdown();
                LOGGER.warn("[LifeCycleManager] Destruction of the end");
            }
        }));
    }

    private void init() {
        this.lifeCycleResources = new CopyOnWriteArrayList<AbstractLifeCycle>();
    }

    public static ResourceLifeCycleManager getInstance() {
        return INSTANCE;
    }

    /**
     * Destroy all of the life cycle resources which are managed by ResourceLifeCycleManager.
     *
     */
    public static void shutdown() {
        if (!CLOSED.compareAndSet(false, true)) {
            return;
        }

        List<AbstractLifeCycle> instances = INSTANCE.lifeCycleResources;
        for (AbstractLifeCycle instance : instances) {
            INSTANCE.destroy(instance);
        }
    }

    /**
     * Destroy the life cycle resource instance.
     *
     * @param instance the life cycle resource instance which is need to be destroyed.
     */
    public void destroy(AbstractLifeCycle instance) {
        final Object monitor = lockers.get(instance);
        if (monitor == null) {
            return;
        }
        synchronized (monitor) {
            try {
                // the life cycle resources which managed are do stop method.
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Life cycle resources do stop");
                }
                instance.stop();
                INSTANCE.lifeCycleResources.remove(instance);
                INSTANCE.lockers.remove(instance);
            } catch (Exception e) {
                LOGGER.error("An exception occurred during resource do stop : {}", e);
            }
        }
    }


    /**
     * Cancel the specified lifecycle resource instance management.
     *
     * @param instance the management life cycle resource instances;
     *
     */
    public void deregister(AbstractLifeCycle instance) {
        if (this.lifeCycleResources.contains(instance)) {
            final Object monitor = lockers.get(instance);
            synchronized (monitor) {
                this.lifeCycleResources.remove(instance);
                this.lockers.remove(instance);
            }
        }
    }

    /**
     * Register the life cycle resource instances into the lifeCycleResources lists.
     *
     * @param instance the management life cycle resource instances.
     */
    public void register(AbstractLifeCycle instance) {
        if (!lifeCycleResources.contains(instance)) {
            synchronized(this) {
                lockers.put(instance, new Object());
            }
            this.lifeCycleResources.add(instance);
        }
    }
}
