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
import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.listener.AbstractSharedListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.listener.impl.AbstractConfigChangeListener;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.TenantUtil;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.NumberUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Listener Management.
 *
 * @author Nacos
 */
public class CacheData {
    
    private static final Logger LOGGER = LogUtils.logger(CacheData.class);
    
    private static long notifyWarnTimeout = 60000;
    
    static {
        String notifyTimeouts = System.getProperty("nacos.listener.notify.warn.timeout");
        if (StringUtils.isNotBlank(notifyTimeouts) && NumberUtils.isDigits(notifyTimeouts)) {
            notifyWarnTimeout = Long.valueOf(notifyTimeouts);
            LOGGER.info("config listener notify warn timeout millis is set to {}", notifyWarnTimeout);
        } else {
            LOGGER.info("config listener notify warn timeout millis use default {} millis ", notifyWarnTimeout);
            
        }
    }
    
    static ScheduledThreadPoolExecutor scheduledExecutor;
    
    static ScheduledThreadPoolExecutor getNotifyBlockMonitor() {
        if (scheduledExecutor == null) {
            synchronized (CacheData.class) {
                if (scheduledExecutor == null) {
                    scheduledExecutor = new ScheduledThreadPoolExecutor(1,
                            new NameThreadFactory("com.alibaba.nacos.client.notify.block.monitor"),
                            new ThreadPoolExecutor.DiscardPolicy());
                    scheduledExecutor.setRemoveOnCancelPolicy(true);
                }
            }
        }
        return scheduledExecutor;
    }
    
    static boolean initSnapshot;
    
    static {
        initSnapshot = NacosClientProperties.PROTOTYPE.getBoolean("nacos.cache.data.init.snapshot", true);
        LOGGER.info("nacos.cache.data.init.snapshot = {} ", initSnapshot);
    }
    
    public final String envName;
    
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
    
    /**
     * local cache change timestamp.
     */
    private final AtomicLong lastModifiedTs = new AtomicLong(0);
    
    /**
     * notify change flag,for notify&sync concurrent control. 1.reset to false if starting to sync with server. 2.update
     * to true if receive config change notification.
     */
    private final AtomicBoolean receiveNotifyChanged = new AtomicBoolean(false);
    
    private int taskId;
    
    private volatile boolean isInitializing = true;
    
    /**
     * if is cache data md5 sync with the server.
     */
    private final AtomicBoolean isConsistentWithServer = new AtomicBoolean();
    
    /**
     * if is cache data is discard,need to remove.
     */
    private volatile boolean isDiscard = false;
    
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
    
    public AtomicBoolean getReceiveNotifyChanged() {
        return receiveNotifyChanged;
    }
    
    /**
     * Getter method for property <tt>lastModifiedTs</tt>.
     *
     * @return property value of lastModifiedTs
     */
    public AtomicLong getLastModifiedTs() {
        return lastModifiedTs;
    }
    
    /**
     * Setter method for property <tt>lastModifiedTs</tt>.
     *
     * @param lastModifiedTs value to be assigned to property lastModifiedTs
     */
    public void setLastModifiedTs(long lastModifiedTs) {
        this.lastModifiedTs.set(lastModifiedTs);
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
    public void addListener(Listener listener) throws NacosException {
        if (null == listener) {
            throw new IllegalArgumentException("listener is null");
        }
        ManagerListenerWrap wrap;
        if (listener instanceof AbstractConfigChangeListener) {
            ConfigResponse cr = new ConfigResponse();
            cr.setDataId(dataId);
            cr.setGroup(group);
            cr.setContent(content);
            cr.setEncryptedDataKey(encryptedDataKey);
            configFilterChainManager.doFilter(null, cr);
            String contentTmp = cr.getContent();
            wrap = new ManagerListenerWrap(listener, md5, contentTmp);
        } else {
            wrap = new ManagerListenerWrap(listener, md5);
        }
        
        if (listeners.addIfAbsent(wrap)) {
            LOGGER.info("[{}] [add-listener] ok, tenant={}, dataId={}, group={}, cnt={}", envName, tenant, dataId,
                    group, listeners.size());
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
            LOGGER.info("[{}] [remove-listener] ok, dataId={}, group={},tenant={}, cnt={}", envName, dataId, group,
                    tenant, listeners.size());
        }
    }
    
    /**
     * Returns the iterator on the listener list, read-only. It is guaranteed not to return NULL.
     */
    public List<Listener> getListeners() {
        List<Listener> result = new ArrayList<>();
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
    
    class LongNotifyHandler implements Runnable {
        
        public LongNotifyHandler(String listenerClass, String dataId, String group, String tenant, String md5,
                long timeoutMills, Thread thread) {
            this.listenerClass = listenerClass;
            this.dataId = dataId;
            this.group = group;
            this.tenant = tenant;
            this.md5 = md5;
            this.timeoutMills = timeoutMills;
            this.thread = thread;
        }
        
        String listenerClass;
        
        long startTime = System.currentTimeMillis();
        
        long timeoutMills;
        
        String dataId;
        
        String group;
        
        String tenant;
        
        String md5;
        
        Thread thread;
        
        @Override
        public void run() {
            String blockTrace = getTrace(thread.getStackTrace(), 5);
            LOGGER.warn("[{}] [notify-block-monitor] dataId={}, group={},tenant={}, md5={}, "
                            + "receiveConfigInfo execute over {} mills，thread trace block : {}", envName, dataId, group, tenant,
                    md5, timeoutMills, blockTrace);
            NotifyCenter.publishEvent(
                    new ChangeNotifyBlockEvent(this.listenerClass, dataId, group, tenant, this.startTime,
                            System.currentTimeMillis(), blockTrace));
        }
        
    }
    
    private static String getTrace(StackTraceElement[] stackTrace, int traceDeep) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        int deep = 0;
        for (StackTraceElement element : stackTrace) {
            stringBuilder.append("\tat " + element + "\n");
            deep++;
            if (traceDeep > 0 && deep > traceDeep) {
                stringBuilder.append("\tat ... \n");
                break;
            }
        }
        return stringBuilder.toString();
    }
    
    private void safeNotifyListener(final String dataId, final String group, final String content, final String type,
            final String md5, final String encryptedDataKey, final ManagerListenerWrap listenerWrap) {
        final Listener listener = listenerWrap.listener;
        if (listenerWrap.inNotifying) {
            LOGGER.warn(
                    "[{}] [notify-currentSkip] dataId={}, group={},tenant={}, md5={}, listener={}, listener is not finish yet,will try next time.",
                    envName, dataId, group, tenant, md5, listener);
            return;
        }
        NotifyTask job = new NotifyTask() {
            
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                ClassLoader myClassLoader = Thread.currentThread().getContextClassLoader();
                ClassLoader appClassLoader = listener.getClass().getClassLoader();
                ScheduledFuture<?> timeSchedule = null;
                
                try {
                    if (listener instanceof AbstractSharedListener) {
                        AbstractSharedListener adapter = (AbstractSharedListener) listener;
                        adapter.fillContext(dataId, group);
                        LOGGER.info("[{}] [notify-context] dataId={}, group={},tenant={}, md5={}", envName, dataId,
                                group, tenant, md5);
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
                    timeSchedule = getNotifyBlockMonitor().schedule(
                            new LongNotifyHandler(listener.getClass().getSimpleName(), dataId, group, tenant, md5,
                                    notifyWarnTimeout, Thread.currentThread()), notifyWarnTimeout,
                            TimeUnit.MILLISECONDS);
                    listenerWrap.inNotifying = true;
                    listener.receiveConfigInfo(contentTmp);
                    // compare lastContent and content
                    if (listener instanceof AbstractConfigChangeListener) {
                        Map<String, ConfigChangeItem> data = ConfigChangeHandler.getInstance()
                                .parseChangeData(listenerWrap.lastContent, contentTmp, type);
                        ConfigChangeEvent event = new ConfigChangeEvent(data);
                        ((AbstractConfigChangeListener) listener).receiveConfigChange(event);
                        listenerWrap.lastContent = contentTmp;
                    }
                    
                    listenerWrap.lastCallMd5 = md5;
                    LOGGER.info(
                            "[{}] [notify-ok] dataId={}, group={},tenant={}, md5={}, listener={} ,job run cost={} millis.",
                            envName, dataId, group, tenant, md5, listener, (System.currentTimeMillis() - start));
                } catch (NacosException ex) {
                    LOGGER.error(
                            "[{}] [notify-error] dataId={}, group={},tenant={},md5={}, listener={} errCode={} errMsg={},stackTrace :{}",
                            envName, dataId, group, tenant, md5, listener, ex.getErrCode(), ex.getErrMsg(),
                            getTrace(ex.getStackTrace(), 3));
                } catch (Throwable t) {
                    LOGGER.error("[{}] [notify-error] dataId={}, group={},tenant={}, md5={}, listener={} tx={}",
                            envName, dataId, group, tenant, md5, listener, getTrace(t.getStackTrace(), 3));
                } finally {
                    listenerWrap.inNotifying = false;
                    Thread.currentThread().setContextClassLoader(myClassLoader);
                    if (timeSchedule != null) {
                        timeSchedule.cancel(true);
                    }
                }
            }
        };
        
        try {
            if (null != listener.getExecutor()) {
                LOGGER.info(
                        "[{}] [notify-listener] task submitted to user executor, dataId={}, group={},tenant={}, md5={}, listener={} ",
                        envName, dataId, group, tenant, md5, listener);
                job.async = true;
                listener.getExecutor().execute(job);
            } else {
                LOGGER.info(
                        "[{}] [notify-listener] task execute in nacos thread, dataId={}, group={},tenant={}, md5={}, listener={} ",
                        envName, dataId, group, tenant, md5, listener);
                job.run();
            }
        } catch (Throwable t) {
            LOGGER.error("[{}] [notify-listener-error] dataId={}, group={},tenant={}, md5={}, listener={} throwable={}",
                    envName, dataId, group, tenant, md5, listener, t.getCause());
        }
    }
    
    @SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
    abstract class NotifyTask implements Runnable {
        
        boolean async = false;
        
        public boolean isAsync() {
            return async;
        }
        
        public void setAsync(boolean async) {
            this.async = async;
        }
        
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
     * @return
     */
    public boolean isConsistentWithServer() {
        return isConsistentWithServer.get();
    }
    
    public void setConsistentWithServer(boolean consistentWithServer) {
        isConsistentWithServer.set(consistentWithServer);
    }
    
    public boolean isDiscard() {
        return isDiscard;
    }
    
    public void setDiscard(boolean discard) {
        isDiscard = discard;
    }
    
    public CacheData(ConfigFilterChainManager configFilterChainManager, String envName, String dataId, String group) {
        this(configFilterChainManager, envName, dataId, group, TenantUtil.getUserTenantForAcm());
    }
    
    public CacheData(ConfigFilterChainManager configFilterChainManager, String envName, String dataId, String group,
            String tenant) {
        if (null == dataId || null == group) {
            throw new IllegalArgumentException("dataId=" + dataId + ", group=" + group);
        }
        this.configFilterChainManager = configFilterChainManager;
        this.envName = envName;
        this.dataId = dataId;
        this.group = group;
        this.tenant = tenant;
        this.listeners = new CopyOnWriteArrayList<>();
        this.isInitializing = true;
        if (initSnapshot) {
            this.content = loadCacheContentFromDiskLocal(envName, dataId, group, tenant);
            this.encryptedDataKey = loadEncryptedDataKeyFromDiskLocal(envName, dataId, group, tenant);
            this.md5 = getMd5String(this.content);
        }
    }
    
    // ==================
    
    public String getEncryptedDataKey() {
        return encryptedDataKey;
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        this.encryptedDataKey = encryptedDataKey;
    }
    
    private String loadEncryptedDataKeyFromDiskLocal(String envName, String dataId, String group, String tenant) {
        String encryptedDataKey = LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(envName, dataId, group,
                tenant);
        
        if (encryptedDataKey != null) {
            return encryptedDataKey;
        }
        
        return LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(envName, dataId, group, tenant);
    }
    
    private static class ManagerListenerWrap {
        
        boolean inNotifying = false;
        
        final Listener listener;
        
        String lastCallMd5 = Constants.NULL;
        
        /**
         * here is a decryptContent.
         */
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
