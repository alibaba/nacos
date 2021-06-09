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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigChangeEvent;
import com.alibaba.nacos.api.config.listener.AbstractSharedListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.listener.impl.AbstractConfigChangeListener;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.TenantUtil;
import com.alibaba.nacos.common.utils.MD5Utils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Listener Management.
 *
 * @author Nacos
 */
public class CacheData {
    
    static final int CONCURRENCY = 5;
    
    static ThreadFactory internalNotifierFactory = r -> {
        Thread t = new Thread(r);
        t.setName("nacos.client.cachedata.internal.notifier");
        t.setDaemon(true);
        return t;
    };
    
    static final ThreadPoolExecutor INTERNAL_NOTIFIER = new ThreadPoolExecutor(0, CONCURRENCY, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), internalNotifierFactory);
    
    private static final Logger LOGGER = LogUtils.logger(CacheData.class);
    
    private final String name;
    
    private final ConfigFilterChainManager configFilterChainManager;
    
    public final String dataId;
    
    public final String group;
    
    public final String tenant;
    
    private final CopyOnWriteArrayList<ManagerListenerWrap> listeners;
    
    private volatile String md5;
    
    /**
     * whether use local config.
     */
    private volatile boolean isUseLocalConfig = false;
    
    /**
     * last modify time.
     */
    private volatile long localConfigLastModified;
    
    private volatile String content;
    
    private volatile String encryptedDataKey;
    
    private volatile long lastModifiedTs;
    
    private int taskId;
    
    private volatile boolean isInitializing = true;
    
    /**
     * if is cache data md5 sync with the server.
     */
    private volatile boolean isSyncWithServer = false;
    
    private String type;
    
    public boolean isInitializing() {
        return isInitializing;
    }
    
    public void setInitializing(boolean isInitializing) {
        this.isInitializing = isInitializing;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
        this.md5 = getMd5String(this.content);
    }
    
    /**
     * Getter method for property <tt>lastModifiedTs</tt>.
     *
     * @return property value of lastModifiedTs
     */
    public long getLastModifiedTs() {
        return lastModifiedTs;
    }
    
    /**
     * Setter method for property <tt>lastModifiedTs</tt>.
     *
     * @param lastModifiedTs value to be assigned to property lastModifiedTs
     */
    public void setLastModifiedTs(long lastModifiedTs) {
        this.lastModifiedTs = lastModifiedTs;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    /**
     * Add listener if CacheData already set new content, Listener should init lastCallMd5 by CacheData.md5
     *
     * @param listener listener
     */
    public void addListener(Listener listener) {
        if (null == listener) {
            throw new IllegalArgumentException("listener is null");
        }
        ManagerListenerWrap wrap =
                (listener instanceof AbstractConfigChangeListener) ? new ManagerListenerWrap(listener, md5, content)
                        : new ManagerListenerWrap(listener, md5);
        
        if (listeners.addIfAbsent(wrap)) {
            LOGGER.info("[{}] [add-listener] ok, tenant={}, dataId={}, group={}, cnt={}", name, tenant, dataId, group,
                    listeners.size());
        }
    }
    
    /**
     * Remove listener.
     *
     * @param listener listener
     */
    public void removeListener(Listener listener) {
        if (null == listener) {
            throw new IllegalArgumentException("listener is null");
        }
        ManagerListenerWrap wrap = new ManagerListenerWrap(listener);
        if (listeners.remove(wrap)) {
            LOGGER.info("[{}] [remove-listener] ok, dataId={}, group={}, cnt={}", name, dataId, group,
                    listeners.size());
        }
    }
    
    /**
     * Returns the iterator on the listener list, read-only. It is guaranteed not to return NULL.
     */
    public List<Listener> getListeners() {
        List<Listener> result = new ArrayList<Listener>();
        for (ManagerListenerWrap wrap : listeners) {
            result.add(wrap.listener);
        }
        return result;
    }
    
    public long getLocalConfigInfoVersion() {
        return localConfigLastModified;
    }
    
    public void setLocalConfigInfoVersion(long localConfigLastModified) {
        this.localConfigLastModified = localConfigLastModified;
    }
    
    public boolean isUseLocalConfigInfo() {
        return isUseLocalConfig;
    }
    
    public void setUseLocalConfigInfo(boolean useLocalConfigInfo) {
        this.isUseLocalConfig = useLocalConfigInfo;
        if (!useLocalConfigInfo) {
            localConfigLastModified = -1;
        }
    }
    
    public int getTaskId() {
        return taskId;
    }
    
    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dataId == null) ? 0 : dataId.hashCode());
        result = prime * result + ((group == null) ? 0 : group.hashCode());
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (null == obj || obj.getClass() != getClass()) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        CacheData other = (CacheData) obj;
        return dataId.equals(other.dataId) && group.equals(other.group);
    }
    
    @Override
    public String toString() {
        return "CacheData [" + dataId + ", " + group + "]";
    }
    
    void checkListenerMd5() {
        for (ManagerListenerWrap wrap : listeners) {
            if (!md5.equals(wrap.lastCallMd5)) {
                safeNotifyListener(dataId, group, content, type, md5, encryptedDataKey, wrap);
            }
        }
    }
    
    /**
     * check if all listeners md5 is equal with cache data.
     */
    public boolean checkListenersMd5Consistent() {
        for (ManagerListenerWrap wrap : listeners) {
            if (!md5.equals(wrap.lastCallMd5)) {
                return false;
            }
        }
        return true;
    }
    
    private void safeNotifyListener(final String dataId, final String group, final String content, final String type,
            final String md5, final String encryptedDataKey, final ManagerListenerWrap listenerWrap) {
        final Listener listener = listenerWrap.listener;
        if (listenerWrap.inNotifying) {
            LOGGER.warn(
                    "[{}] [notify-currentSkip] dataId={}, group={}, md5={}, listener={}, listener is not finish yet,will try next time.",
                    name, dataId, group, md5, listener);
            return;
        }
        Runnable job = new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                ClassLoader myClassLoader = Thread.currentThread().getContextClassLoader();
                ClassLoader appClassLoader = listener.getClass().getClassLoader();
                try {
                    if (listener instanceof AbstractSharedListener) {
                        AbstractSharedListener adapter = (AbstractSharedListener) listener;
                        adapter.fillContext(dataId, group);
                        LOGGER.info("[{}] [notify-context] dataId={}, group={}, md5={}", name, dataId, group, md5);
                    }
                    // Before executing the callback, set the thread classloader to the classloader of
                    // the specific webapp to avoid exceptions or misuses when calling the spi interface in
                    // the callback method (this problem occurs only in multi-application deployment).
                    Thread.currentThread().setContextClassLoader(appClassLoader);
                    
                    ConfigResponse cr = new ConfigResponse();
                    cr.setDataId(dataId);
                    cr.setGroup(group);
                    cr.setContent(content);
                    cr.setEncryptedDataKey(encryptedDataKey);
                    configFilterChainManager.doFilter(null, cr);
                    String contentTmp = cr.getContent();
                    listenerWrap.inNotifying = true;
                    listener.receiveConfigInfo(contentTmp);
                    // compare lastContent and content
                    if (listener instanceof AbstractConfigChangeListener) {
                        Map data = ConfigChangeHandler.getInstance()
                                .parseChangeData(listenerWrap.lastContent, content, type);
                        ConfigChangeEvent event = new ConfigChangeEvent(data);
                        ((AbstractConfigChangeListener) listener).receiveConfigChange(event);
                        listenerWrap.lastContent = content;
                    }
                    
                    listenerWrap.lastCallMd5 = md5;
                    LOGGER.info("[{}] [notify-ok] dataId={}, group={}, md5={}, listener={} ,cost={} millis.", name,
                            dataId, group, md5, listener, (System.currentTimeMillis() - start));
                } catch (NacosException ex) {
                    LOGGER.error("[{}] [notify-error] dataId={}, group={}, md5={}, listener={} errCode={} errMsg={}",
                            name, dataId, group, md5, listener, ex.getErrCode(), ex.getErrMsg());
                } catch (Throwable t) {
                    LOGGER.error("[{}] [notify-error] dataId={}, group={}, md5={}, listener={} tx={}", name, dataId,
                            group, md5, listener, t.getCause());
                } finally {
                    listenerWrap.inNotifying = false;
                    Thread.currentThread().setContextClassLoader(myClassLoader);
                }
            }
        };
        
        final long startNotify = System.currentTimeMillis();
        try {
            if (null != listener.getExecutor()) {
                listener.getExecutor().execute(job);
            } else {
                try {
                    INTERNAL_NOTIFIER.submit(job);
                } catch (RejectedExecutionException rejectedExecutionException) {
                    LOGGER.warn(
                            "[{}] [notify-blocked] dataId={}, group={}, md5={}, listener={}, no available internal notifier,will sync notifier ",
                            name, dataId, group, md5, listener);
                    job.run();
                } catch (Throwable throwable) {
                    LOGGER.error(
                            "[{}] [notify-blocked] dataId={}, group={}, md5={}, listener={}, submit internal async task fail,throwable= ",
                            name, dataId, group, md5, listener, throwable);
                    job.run();
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[{}] [notify-error] dataId={}, group={}, md5={}, listener={} throwable={}", name, dataId,
                    group, md5, listener, t.getCause());
        }
        final long finishNotify = System.currentTimeMillis();
        LOGGER.info("[{}] [notify-listener] time cost={}ms in ClientWorker, dataId={}, group={}, md5={}, listener={} ",
                name, (finishNotify - startNotify), dataId, group, md5, listener);
    }
    
    public static String getMd5String(String config) {
        return (null == config) ? Constants.NULL : MD5Utils.md5Hex(config, Constants.ENCODE);
    }
    
    private String loadCacheContentFromDiskLocal(String name, String dataId, String group, String tenant) {
        String content = LocalConfigInfoProcessor.getFailover(name, dataId, group, tenant);
        content = (null != content) ? content : LocalConfigInfoProcessor.getSnapshot(name, dataId, group, tenant);
        return content;
    }
    
    /**
     * 1.first add listener.default is false;need to check. 2.receive config change notify,set false;need to check.
     * 3.last listener is remove,set to false;need to check
     *
     * @return the flag if sync with server
     */
    public boolean isSyncWithServer() {
        return isSyncWithServer;
    }
    
    public void setSyncWithServer(boolean syncWithServer) {
        isSyncWithServer = syncWithServer;
    }
    
    public CacheData(ConfigFilterChainManager configFilterChainManager, String name, String dataId, String group) {
        if (null == dataId || null == group) {
            throw new IllegalArgumentException("dataId=" + dataId + ", group=" + group);
        }
        this.name = name;
        this.configFilterChainManager = configFilterChainManager;
        this.dataId = dataId;
        this.group = group;
        this.tenant = TenantUtil.getUserTenantForAcm();
        listeners = new CopyOnWriteArrayList<ManagerListenerWrap>();
        this.isInitializing = true;
        this.content = loadCacheContentFromDiskLocal(name, dataId, group, tenant);
        this.md5 = getMd5String(content);
        this.encryptedDataKey = loadEncryptedDataKeyFromDiskLocal(name, dataId, group, tenant);
    }
    
    public CacheData(ConfigFilterChainManager configFilterChainManager, String name, String dataId, String group,
            String tenant) {
        if (null == dataId || null == group) {
            throw new IllegalArgumentException("dataId=" + dataId + ", group=" + group);
        }
        this.name = name;
        this.configFilterChainManager = configFilterChainManager;
        this.dataId = dataId;
        this.group = group;
        this.tenant = tenant;
        listeners = new CopyOnWriteArrayList<ManagerListenerWrap>();
        this.isInitializing = true;
        this.content = loadCacheContentFromDiskLocal(name, dataId, group, tenant);
        this.md5 = getMd5String(content);
    }
    
    public String getEncryptedDataKey() {
        return encryptedDataKey;
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        this.encryptedDataKey = encryptedDataKey;
    }
    
    private String loadEncryptedDataKeyFromDiskLocal(String name, String dataId, String group, String tenant) {
        String encryptedDataKey = LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(name, dataId, group, tenant);
        
        if (encryptedDataKey != null) {
            return encryptedDataKey;
        }
        
        return LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(name, dataId, group, tenant);
    }
    
    private static class ManagerListenerWrap {
        
        boolean inNotifying = false;
        
        final Listener listener;
        
        String lastCallMd5 = CacheData.getMd5String(null);
        
        String lastContent = null;
        
        ManagerListenerWrap(Listener listener) {
            this.listener = listener;
        }
        
        ManagerListenerWrap(Listener listener, String md5) {
            this.listener = listener;
            this.lastCallMd5 = md5;
        }
        
        ManagerListenerWrap(Listener listener, String md5, String lastContent) {
            this.listener = listener;
            this.lastCallMd5 = md5;
            this.lastContent = lastContent;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (null == obj || obj.getClass() != getClass()) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            ManagerListenerWrap other = (ManagerListenerWrap) obj;
            return listener.equals(other.listener);
        }
        
        @Override
        public int hashCode() {
            return super.hashCode();
        }
        
    }
}
