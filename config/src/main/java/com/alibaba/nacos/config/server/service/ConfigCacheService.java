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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigCache;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.model.gray.GrayRule;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.api.common.Constants.CLIENT_IP;
import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;
import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;
import static com.alibaba.nacos.config.server.constant.Constants.NULL;
import static com.alibaba.nacos.config.server.constant.Constants.PERSIST_ENCODE;
import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;
import static com.alibaba.nacos.config.server.utils.LogUtil.DUMP_LOG;
import static com.alibaba.nacos.config.server.utils.LogUtil.FATAL_LOG;

/**
 * Config service.
 *
 * @author Nacos
 */
public class ConfigCacheService {
    
    private static final String NO_SPACE_CN = "设备上没有空间";
    
    private static final String NO_SPACE_EN = "No space left on device";
    
    private static final String DISK_QUOTA_CN = "超出磁盘限额";
    
    private static final String DISK_QUOTA_EN = "Disk quota exceeded";
    
    /**
     * groupKey -> cacheItem.
     */
    private static final ConcurrentHashMap<String, CacheItem> CACHE = new ConcurrentHashMap<>();
    
    public static int groupCount() {
        return CACHE.size();
    }
    
    /**
     * Save config file and update md5 value in cache.
     *
     * @param dataId         dataId string value.
     * @param group          group string value.
     * @param tenant         tenant string value.
     * @param content        content string value.
     * @param md5            md5 of persist.
     * @param lastModifiedTs lastModifiedTs.
     * @param type           file type.
     * @return dumpChange success or not.
     */
    public static boolean dumpWithMd5(String dataId, String group, String tenant, String content, String md5,
            long lastModifiedTs, String type, String encryptedDataKey) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        CacheItem ci = makeSure(groupKey, encryptedDataKey);
        ci.setType(type);
        final int lockResult = tryWriteLock(groupKey);
        
        if (lockResult < 0) {
            DUMP_LOG.warn("[dump-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            
            //check timestamp
            boolean lastModifiedOutDated = lastModifiedTs < ConfigCacheService.getLastModifiedTs(groupKey);
            if (lastModifiedOutDated) {
                DUMP_LOG.warn("[dump-ignore] timestamp is outdated,groupKey={}", groupKey);
                return true;
            }
            
            boolean newLastModified = lastModifiedTs > ConfigCacheService.getLastModifiedTs(groupKey);
            
            if (md5 == null) {
                md5 = MD5Utils.md5Hex(content, PERSIST_ENCODE);
            }
            
            //check md5 & update local disk cache.
            String localContentMd5 = ConfigCacheService.getContentMd5(groupKey);
            boolean md5Changed = !md5.equals(localContentMd5);
            if (md5Changed) {
                DUMP_LOG.info("[dump] md5 changed, save to disk cache ,groupKey={}, newMd5={},oldMd5={}", groupKey, md5,
                        localContentMd5);
                ConfigDiskServiceFactory.getInstance().saveToDisk(dataId, group, tenant, content);
            } else {
                DUMP_LOG.warn("[dump-ignore] ignore to save to disk cache. md5 consistent,groupKey={}, md5={}",
                        groupKey, md5);
            }
            
            //check  md5 and timestamp & update local jvm cache.
            if (md5Changed) {
                DUMP_LOG.info(
                        "[dump] md5 changed, update md5 and timestamp in jvm cache ,groupKey={}, newMd5={},oldMd5={},lastModifiedTs={}",
                        groupKey, md5, localContentMd5, lastModifiedTs);
                updateMd5(groupKey, md5, lastModifiedTs, encryptedDataKey);
            } else if (newLastModified) {
                DUMP_LOG.info(
                        "[dump] md5 consistent ,timestamp changed, update timestamp only in jvm cache ,groupKey={},lastModifiedTs={}",
                        groupKey, lastModifiedTs);
                updateTimeStamp(groupKey, lastModifiedTs, encryptedDataKey);
            } else {
                DUMP_LOG.warn(
                        "[dump-ignore] ignore to save to jvm cache. md5 consistent and no new timestamp changed.groupKey={}",
                        groupKey);
            }
            
            return true;
        } catch (IOException ioe) {
            DUMP_LOG.error("[dump-exception] save disk error. " + groupKey + ", " + ioe);
            if (ioe.getMessage() != null) {
                String errMsg = ioe.getMessage();
                if (errMsg.contains(NO_SPACE_CN) || errMsg.contains(NO_SPACE_EN) || errMsg.contains(DISK_QUOTA_CN)
                        || errMsg.contains(DISK_QUOTA_EN)) {
                    // Protect from disk full.
                    FATAL_LOG.error("Local Disk Full,Exit", ioe);
                    EnvUtil.systemExit();
                }
            }
            return false;
        } finally {
            releaseWriteLock(groupKey);
        }
        
    }
    
    /**
     * Save config file and update md5 value in cache.
     *
     * @param dataId           dataId string value.
     * @param group            group string value.
     * @param tenant           tenant string value.
     * @param content          content string value.
     * @param lastModifiedTs   lastModifiedTs.
     * @param type             file type.
     * @param encryptedDataKey encryptedDataKey.
     * @return dumpChange success or not.
     */
    public static boolean dump(String dataId, String group, String tenant, String content, long lastModifiedTs,
            String type, String encryptedDataKey) {
        return dumpWithMd5(dataId, group, tenant, content, null, lastModifiedTs, type, encryptedDataKey);
    }
    
    /**
     * Save gray config file and update md5 value in cache.
     *
     * @param dataId         dataId string value.
     * @param group          group string value.
     * @param tenant         tenant string value.
     * @param grayName       grayName string value.
     * @param grayRule       grayRule string value.
     * @param content        content string value.
     * @param lastModifiedTs lastModifiedTs.
     * @return dumpChange success or not.
     */
    public static boolean dumpGray(String dataId, String group, String tenant, String grayName, String grayRule,
            String content, long lastModifiedTs, String encryptedDataKey) {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        
        makeSure(groupKey, null);
        final int lockResult = tryWriteLock(groupKey);
        
        if (lockResult < 0) {
            DUMP_LOG.warn("[dump-gray-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            
            //check timestamp
            long localGrayLastModifiedTs = ConfigCacheService.getGrayLastModifiedTs(groupKey, grayName);
            
            boolean timestampOutdated = lastModifiedTs < localGrayLastModifiedTs;
            if (timestampOutdated) {
                DUMP_LOG.warn("[dump-gray-ignore] timestamp is outdated,groupKey={}", groupKey);
                return true;
            }
            
            boolean timestampChanged = lastModifiedTs > localGrayLastModifiedTs;
            
            final String md5 = MD5Utils.md5Hex(content, ENCODE_UTF8);
            
            String localContentGrayMd5 = ConfigCacheService.getContentGrayMd5(groupKey, grayName);
            boolean md5Changed = !md5.equals(localContentGrayMd5);
            
            GrayRule localGrayRule = ConfigCacheService.getGrayRule(groupKey, grayName);
            GrayRule grayRuleNew = GrayRuleManager.constructGrayRule(
                    GrayRuleManager.deserializeConfigGrayPersistInfo(grayRule));
            if (grayRuleNew == null) {
                DUMP_LOG.warn("[dump-gray-exception] . " + groupKey + ",  unknown gray rule for  gray name" + grayName);
                return false;
            }
            
            boolean grayRuleChanged = !grayRuleNew.equals(localGrayRule);
            
            if (md5Changed) {
                DUMP_LOG.info(
                        "[dump-gray] md5 changed, update local jvm cache& local disk cache, groupKey={},grayName={}, "
                                + "newMd5={},oldMd5={}, newGrayRule={}, oldGrayRule={},lastModifiedTs={}", groupKey,
                        grayName, md5, localContentGrayMd5, grayRule, localGrayRule, lastModifiedTs);
                updateGrayMd5(groupKey, grayName, grayRule, md5, lastModifiedTs, encryptedDataKey);
                ConfigDiskServiceFactory.getInstance().saveGrayToDisk(dataId, group, tenant, grayName, content);
                
            } else if (grayRuleChanged) {
                DUMP_LOG.info("[dump-gray] gray rule changed, update local jvm cache, groupKey={},grayName={}, "
                                + "newMd5={},oldMd5={}, newGrayRule={}, oldGrayRule={},lastModifiedTs={}", groupKey, grayName,
                        md5, localContentGrayMd5, grayRule, localGrayRule, lastModifiedTs);
                updateGrayRule(groupKey, grayName, grayRule, lastModifiedTs, encryptedDataKey);
            } else if (timestampChanged) {
                DUMP_LOG.info(
                        "[dump-gray] timestamp changed, update last modified in local jvm cache, groupKey={},grayName={},"
                                + "grayLastModifiedTs={},oldgrayLastModifiedTs={}", groupKey, grayName, lastModifiedTs,
                        localGrayLastModifiedTs);
                updateGrayTimeStamp(groupKey, grayName, lastModifiedTs);
                
            } else {
                DUMP_LOG.warn("[dump-gray-ignore] md5 & timestamp not changed. groupKey={},grayName={}", groupKey,
                        grayName);
            }
            return true;
        } catch (IOException ioe) {
            DUMP_LOG.error("[dump-gray-exception] save disk error. " + groupKey + ", " + ioe.toString(), ioe);
            return false;
        } finally {
            releaseWriteLock(groupKey);
        }
    }
    
    /**
     * Delete gray config file, and delete cache.
     *
     * @param dataId   dataId string value.
     * @param group    group string value.
     * @param tenant   tenant string value.
     * @param grayName grayName string value.
     * @return remove success or not.
     */
    public static boolean removeGray(String dataId, String group, String tenant, String grayName) {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        final int lockResult = tryWriteLock(groupKey);
        
        // If data is non-existent.
        if (0 == lockResult) {
            DUMP_LOG.info("[remove-ok] {} not exist.", groupKey);
            return true;
        }
        
        // try to lock failed
        if (lockResult < 0) {
            DUMP_LOG.warn("[remove-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            DUMP_LOG.info("[remove-gray-ok] remove gray in local disk cache,grayName={},groupKey={} ", grayName,
                    groupKey);
            ConfigDiskServiceFactory.getInstance().removeConfigInfo4Gray(dataId, group, tenant, grayName);
            
            CacheItem ci = CACHE.get(groupKey);
            if (ci.getConfigCacheGray() != null) {
                ci.getConfigCacheGray().remove(grayName);
                if (ci.getConfigCacheGray().isEmpty()) {
                    ci.clearConfigGrays();
                } else {
                    ci.sortConfigGray();
                }
            }
            
            DUMP_LOG.info("[remove-gray-ok] remove gray in local jvm cache,grayName={},groupKey={} ", grayName,
                    groupKey);
            
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey));
            return true;
        } finally {
            releaseWriteLock(groupKey);
        }
    }
    
    /**
     * Delete config file, and delete cache.
     *
     * @param dataId dataId string value.
     * @param group  group string value.
     * @param tenant tenant string value.
     * @return remove success or not.
     */
    public static boolean remove(String dataId, String group, String tenant) {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        final int lockResult = tryWriteLock(groupKey);
        
        // If data is non-existent.
        if (0 == lockResult) {
            DUMP_LOG.info("[remove-ok] {} not exist.", groupKey);
            return true;
        }
        
        // try to lock failed
        if (lockResult < 0) {
            DUMP_LOG.warn("[remove-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            DUMP_LOG.info("[dump] remove  local disk cache,groupKey={} ", groupKey);
            ConfigDiskServiceFactory.getInstance().removeConfigInfo(dataId, group, tenant);
            
            CACHE.remove(groupKey);
            DUMP_LOG.info("[dump] remove  local jvm cache,groupKey={} ", groupKey);
            
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey));
            
            return true;
        } finally {
            releaseWriteLock(groupKey);
        }
    }
    
    /**
     * Update md5 value.
     *
     * @param groupKey       groupKey string value.
     * @param md5Utf8        md5 string value.
     * @param lastModifiedTs lastModifiedTs long value.
     */
    public static void updateMd5(String groupKey, String md5Utf8, long lastModifiedTs, String encryptedDataKey) {
        CacheItem cache = makeSure(groupKey, encryptedDataKey);
        if (cache.getConfigCache().getMd5Utf8() == null || !cache.getConfigCache().getMd5Utf8().equals(md5Utf8)) {
            cache.getConfigCache().setMd5Utf8(md5Utf8);
            cache.getConfigCache().setLastModifiedTs(lastModifiedTs);
            cache.getConfigCache().setEncryptedDataKey(encryptedDataKey);
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey));
        }
    }
    
    /**
     * Update gray md5 value.
     *
     * @param groupKey         groupKey string value.
     * @param grayName         grayName string value.
     * @param grayRule         grayRule string value.
     * @param md5Utf8          md5UTF8 string value.
     * @param lastModifiedTs   lastModifiedTs long value.
     * @param encryptedDataKey encryptedDataKey string value.
     */
    private static void updateGrayMd5(String groupKey, String grayName, String grayRule, String md5Utf8,
            long lastModifiedTs, String encryptedDataKey) {
        CacheItem cache = makeSure(groupKey, null);
        cache.initConfigGrayIfEmpty(grayName);
        ConfigCacheGray configCache = cache.getConfigCacheGray().get(grayName);
        configCache.setMd5Utf8(md5Utf8);
        configCache.setLastModifiedTs(lastModifiedTs);
        configCache.setEncryptedDataKey(encryptedDataKey);
        configCache.resetGrayRule(grayRule);
        cache.sortConfigGray();
        NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey));
    }
    
    /**
     * Get and return content md5 value from cache. Empty string represents no data.
     */
    public static String getContentMd5(String groupKey) {
        return getContentMd5(groupKey, null, null);
    }
    
    public static String getContentMd5(String groupKey, String ip, String tag) {
        return getContentMd5(groupKey, ip, tag, null, ENCODE_UTF8);
    }
    
    public static String getContentMd5(String groupKey, String ip, String tag, Map<String, String> connLabels,
            String encode) {
        CacheItem item = CACHE.get(groupKey);
        if (item == null) {
            return NULL;
        }
        if (connLabels == null && StringUtils.isNotBlank(ip)) {
            connLabels = new HashMap<>(4);
        }
        if (connLabels == null && StringUtils.isNotBlank(tag)) {
            connLabels = new HashMap<>(4);
        }
        
        if (StringUtils.isNotBlank(ip)) {
            connLabels.put(CLIENT_IP, ip);
        }
        if (StringUtils.isNotBlank(tag)) {
            connLabels.put(VIPSERVER_TAG, tag);
        }
        if (item.getSortConfigGrays() != null && connLabels != null && !connLabels.isEmpty()) {
            for (ConfigCacheGray entry : item.getSortConfigGrays()) {
                if (entry.match(connLabels)) {
                    return entry.getMd5(encode);
                }
            }
        }
        String md5 = item.getConfigCache().getMd5(encode);
        return md5 == null ? NULL : md5;
    }
    
    private static void updateGrayRule(String groupKey, String grayName, String grayRule, long lastModifiedTs,
            String encryptedDataKey) {
        CacheItem cache = makeSure(groupKey, null);
        cache.initConfigGrayIfEmpty(grayName);
        ConfigCacheGray configCache = cache.getConfigCacheGray().get(grayName);
        configCache.setLastModifiedTs(lastModifiedTs);
        configCache.setEncryptedDataKey(encryptedDataKey);
        configCache.resetGrayRule(grayRule);
        cache.sortConfigGray();
        NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey));
    }
    
    /**
     * Get and return gray md5 value from cache. Empty string represents no data.
     *
     * @param groupKey groupKey string value.
     * @param grayName grayName string value.
     * @return Content Tag Md5 value.
     */
    public static String getContentGrayMd5(String groupKey, String grayName) {
        CacheItem item = CACHE.get(groupKey);
        if (item == null || item.getConfigCacheGray() == null || !item.getConfigCacheGray().containsKey(grayName)) {
            return NULL;
        }
        return item.getConfigCacheGray().get(grayName).getMd5(ENCODE_UTF8);
    }
    
    public static long getGrayLastModifiedTs(String groupKey, String grayName) {
        CacheItem item = CACHE.get(groupKey);
        if (item.getConfigCacheGray() == null || !item.getConfigCacheGray().containsKey(grayName)) {
            return 0;
        }
        ConfigCache configCacheGray = item.getConfigCacheGray().get(grayName);
        
        return (null != configCacheGray) ? configCacheGray.getLastModifiedTs() : 0;
    }
    
    public static GrayRule getGrayRule(String groupKey, String grayName) {
        CacheItem item = CACHE.get(groupKey);
        if (item == null || item.getConfigCacheGray() == null || !item.getConfigCacheGray().containsKey(grayName)) {
            return null;
        }
        return item.getConfigCacheGray().get(grayName).getGrayRule();
    }
    
    /**
     * Get and return content cache.
     *
     * @param groupKey groupKey string value.
     * @return CacheItem.
     */
    public static CacheItem getContentCache(String groupKey) {
        return CACHE.get(groupKey);
    }
    
    public static long getLastModifiedTs(String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        return (null != item) ? item.getConfigCache().getLastModifiedTs() : 0L;
    }
    
    /**
     * update gray timestamp.
     *
     * @param groupKey       groupKey.
     * @param grayName       grayName.
     * @param lastModifiedTs lastModifiedTs.
     */
    private static void updateGrayTimeStamp(String groupKey, String grayName, long lastModifiedTs) {
        CacheItem cache = makeSure(groupKey, null);
        cache.initConfigGrayIfEmpty(grayName);
        cache.getConfigCacheGray().get(grayName).setLastModifiedTs(lastModifiedTs);
    }
    
    public static boolean isUptodate(String groupKey, String md5) {
        return isUptodate(groupKey, md5, null, null);
    }
    
    public static boolean isUptodate(String groupKey, String md5, String ip, String tag) {
        return isUptodate(groupKey, md5, ip, tag, null);
    }
    
    public static boolean isUptodate(String groupKey, String md5, String ip, String tag,
            Map<String, String> appLabels) {
        String serverMd5 = ConfigCacheService.getContentMd5(groupKey, ip, tag, appLabels, ENCODE_UTF8);
        return StringUtils.equals(md5, serverMd5);
    }
    
    /**
     * Try to add read lock. If it succeeded, then it can call {@link #releaseWriteLock(String)}.And it won't call if
     * failed.
     *
     * @param groupKey groupKey string value.
     * @return 0 - No data and failed. Positive number - lock succeeded. Negative number - lock failed。
     */
    public static int tryReadLock(String groupKey) {
        CacheItem groupItem = CACHE.get(groupKey);
        int result = (null == groupItem) ? 0 : (groupItem.getRwLock().tryReadLock() ? 1 : -1);
        if (result < 0) {
            DEFAULT_LOG.warn("[read-lock] failed, {}, {}", result, groupKey);
        }
        return result;
    }
    
    /**
     * Release readLock.
     *
     * @param groupKey groupKey string value.
     */
    public static void releaseReadLock(String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        if (null != item) {
            item.getRwLock().releaseReadLock();
        }
    }
    
    /**
     * Try to add write lock. If it succeeded, then it can call {@link #releaseWriteLock(String)}.And it won't call if
     * failed.
     *
     * @param groupKey groupKey string value.
     * @return 0 - No data and failed. Positive number 0 - Success. Negative number - lock failed。
     */
    static int tryWriteLock(String groupKey) {
        CacheItem groupItem = CACHE.get(groupKey);
        int result = (null == groupItem) ? 0 : (groupItem.getRwLock().tryWriteLock() ? 1 : -1);
        if (result < 0) {
            DEFAULT_LOG.warn("[write-lock] failed, {}, {}", result, groupKey);
        }
        return result;
    }
    
    static void releaseWriteLock(String groupKey) {
        CacheItem groupItem = CACHE.get(groupKey);
        if (null != groupItem) {
            groupItem.getRwLock().releaseWriteLock();
        }
    }
    
    static CacheItem makeSure(final String groupKey, final String encryptedDataKey) {
        CacheItem item = CACHE.get(groupKey);
        if (null != item) {
            return item;
        }
        CacheItem tmp = new CacheItem(groupKey, encryptedDataKey);
        item = CACHE.putIfAbsent(groupKey, tmp);
        return (null == item) ? tmp : item;
    }
    
    /**
     * update time stamp.
     *
     * @param groupKey         groupKey.
     * @param lastModifiedTs   lastModifiedTs.
     * @param encryptedDataKey encryptedDataKey.
     */
    private static void updateTimeStamp(String groupKey, long lastModifiedTs, String encryptedDataKey) {
        CacheItem cache = makeSure(groupKey, encryptedDataKey);
        cache.getConfigCache().setLastModifiedTs(lastModifiedTs);
    }
    
    private static final int TRY_GET_LOCK_TIMES = 9;
    
    /**
     * try config read lock with spin of try get lock times.
     *
     * @param groupKey group key of config.
     * @return
     */
    public static int tryConfigReadLock(String groupKey) {
        
        // Lock failed by default.
        int lockResult = -1;
        
        // Try to get lock times, max value: 10;
        for (int i = TRY_GET_LOCK_TIMES; i >= 0; --i) {
            lockResult = ConfigCacheService.tryReadLock(groupKey);
            
            // The data is non-existent.
            if (0 == lockResult) {
                break;
            }
            
            // Success
            if (lockResult > 0) {
                break;
            }
            
            // Retry.
            if (i > 0) {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                    LogUtil.PULL_CHECK_LOG.error("An Exception occurred while thread sleep", e);
                }
            }
        }
        
        return lockResult;
    }
}

