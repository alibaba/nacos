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
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigCache;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;
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
                if (NO_SPACE_CN.equals(errMsg) || NO_SPACE_EN.equals(errMsg) || errMsg.contains(DISK_QUOTA_CN)
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
     * Save config file and update md5 value in cache.
     *
     * @param dataId         dataId string value.
     * @param group          group string value.
     * @param tenant         tenant string value.
     * @param content        content string value.
     * @param lastModifiedTs lastModifiedTs.
     * @param betaIps        betaIps string value.
     * @return dumpChange success or not.
     */
    public static boolean dumpBeta(String dataId, String group, String tenant, String content, long lastModifiedTs,
            String betaIps, String encryptedDataKey) {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        
        makeSure(groupKey, null);
        final int lockResult = tryWriteLock(groupKey);
        
        if (lockResult < 0) {
            DUMP_LOG.warn("[dump-beta-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            
            //check timestamp
            boolean timestampOutDated = lastModifiedTs < ConfigCacheService.getBetaLastModifiedTs(groupKey);
            if (timestampOutDated) {
                DUMP_LOG.warn("[dump-beta-ignore] timestamp is outdated,groupKey={}", groupKey);
                return true;
            }
            
            boolean timestampUpdated = lastModifiedTs > ConfigCacheService.getBetaLastModifiedTs(groupKey);
            
            String[] betaIpsArr = betaIps.split(",");
            List<String> betaIpList = Lists.newArrayList(betaIpsArr);
            String md5 = MD5Utils.md5Hex(content, ENCODE_UTF8);
            
            //md5 check & update local disk cache.
            String localContentBetaMd5 = ConfigCacheService.getContentBetaMd5(groupKey);
            boolean md5Changed = !md5.equals(localContentBetaMd5);
            if (md5Changed) {
                DUMP_LOG.info(
                        "[dump-beta] md5 changed, update md5 in local disk cache. groupKey={}, newMd5={}, oldMd5={}",
                        groupKey, md5, localContentBetaMd5);
                ConfigDiskServiceFactory.getInstance().saveBetaToDisk(dataId, group, tenant, content);
            }
            
            //md5 , ip list  timestamp check  and update local jvm cache.
            boolean ipListChanged = !betaIpList.equals(ConfigCacheService.getBetaIps(groupKey));
            if (md5Changed) {
                DUMP_LOG.info(
                        "[dump-beta] md5 changed, update md5 & ip list & timestamp in jvm cache. groupKey={}, newMd5={}, oldMd5={}，lastModifiedTs={}",
                        groupKey, md5, localContentBetaMd5, lastModifiedTs);
                updateBetaMd5(groupKey, md5, betaIpList, lastModifiedTs, encryptedDataKey);
            } else if (ipListChanged) {
                DUMP_LOG.warn("[dump-beta] ip list changed, update ip list & timestamp in jvm cache. groupKey={},"
                                + " newIpList={}, oldIpList={}，lastModifiedTs={}", groupKey, betaIpList,
                        ConfigCacheService.getBetaIps(groupKey), lastModifiedTs);
                updateBetaIpList(groupKey, betaIpList, lastModifiedTs);
            } else if (timestampUpdated) {
                DUMP_LOG.warn(
                        "[dump-beta] timestamp changed, update timestamp in jvm cache. groupKey={}, newLastModifiedTs={}, oldLastModifiedTs={}",
                        groupKey, lastModifiedTs, ConfigCacheService.getBetaLastModifiedTs(groupKey));
                updateBetaTimeStamp(groupKey, lastModifiedTs);
            } else {
                DUMP_LOG.warn(
                        "[dump-beta-ignore] ignore to save jvm cache, md5 & ip list & timestamp no changed. groupKey={}",
                        groupKey);
            }
            return true;
        } catch (IOException ioe) {
            DUMP_LOG.error("[dump-beta-exception] save disk error. " + groupKey + ", " + ioe.toString(), ioe);
            return false;
        } finally {
            releaseWriteLock(groupKey);
        }
    }
    
    /**
     * Save config file and update md5 value in cache.
     *
     * @param dataId         dataId string value.
     * @param group          group string value.
     * @param tenant         tenant string value.
     * @param content        content string value.
     * @param lastModifiedTs lastModifiedTs.
     * @param tag            tag string value.
     * @return dumpChange success or not.
     */
    public static boolean dumpTag(String dataId, String group, String tenant, String tag, String content,
            long lastModifiedTs, String encryptedDataKey4Tag) {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        
        makeSure(groupKey, null);
        final int lockResult = tryWriteLock(groupKey);
        
        if (lockResult < 0) {
            DUMP_LOG.warn("[dump-tag-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            
            //check timestamp
            long localTagLastModifiedTs = ConfigCacheService.getTagLastModifiedTs(groupKey, tag);
            
            boolean timestampOutdated = lastModifiedTs < localTagLastModifiedTs;
            if (timestampOutdated) {
                DUMP_LOG.warn("[dump-tag-ignore] timestamp is outdated,groupKey={}", groupKey);
                return true;
            }
            
            boolean timestampChanged = lastModifiedTs > localTagLastModifiedTs;
            
            final String md5 = MD5Utils.md5Hex(content, ENCODE_UTF8);
            
            String localContentTagMd5 = ConfigCacheService.getContentTagMd5(groupKey, tag);
            boolean md5Changed = !md5.equals(localContentTagMd5);
            
            if (md5Changed) {
                ConfigDiskServiceFactory.getInstance().saveTagToDisk(dataId, group, tenant, tag, content);
            }
            
            if (md5Changed) {
                DUMP_LOG.warn(
                        "[dump-tag] md5 changed, update local jvm cache, groupKey={},tag={}, newMd5={},oldMd5={},lastModifiedTs={}",
                        groupKey, tag, md5, localContentTagMd5, lastModifiedTs);
                updateTagMd5(groupKey, tag, md5, lastModifiedTs, encryptedDataKey4Tag);
            } else if (timestampChanged) {
                DUMP_LOG.warn(
                        "[dump-tag] timestamp changed, update last modified in local jvm cache, groupKey={},tag={},"
                                + "tagLastModifiedTs={},oldTagLastModifiedTs={}", groupKey, tag, lastModifiedTs,
                        localTagLastModifiedTs);
                updateTagTimeStamp(groupKey, tag, lastModifiedTs);
                
            } else {
                DUMP_LOG.warn("[dump-tag-ignore] md5 & timestamp not changed. groupKey={},tag={}", groupKey, tag);
            }
            return true;
        } catch (IOException ioe) {
            DUMP_LOG.error("[dump-tag-exception] save disk error. " + groupKey + ", " + ioe.toString(), ioe);
            return false;
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
     * Delete beta config file, and delete cache.
     *
     * @param dataId dataId string value.
     * @param group  group string value.
     * @param tenant tenant string value.
     * @return remove success or not.
     */
    public static boolean removeBeta(String dataId, String group, String tenant) {
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
            DUMP_LOG.info("[remove-beta-ok] remove beta in local disk cache,groupKey={} ", groupKey);
            ConfigDiskServiceFactory.getInstance().removeConfigInfo4Beta(dataId, group, tenant);
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, true, CACHE.get(groupKey).getIps4Beta()));
            CACHE.get(groupKey).removeBeta();
            DUMP_LOG.info("[remove-beta-ok] remove beta in local jvm cache,groupKey={} ", groupKey);
            
            return true;
        } finally {
            releaseWriteLock(groupKey);
        }
    }
    
    /**
     * Delete tag config file, and delete cache.
     *
     * @param dataId dataId string value.
     * @param group  group string value.
     * @param tenant tenant string value.
     * @param tag    tag string value.
     * @return remove success or not.
     */
    public static boolean removeTag(String dataId, String group, String tenant, String tag) {
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
            DUMP_LOG.info("[remove-tag-ok] remove tag in local disk cache,tag={},groupKey={} ", tag, groupKey);
            ConfigDiskServiceFactory.getInstance().removeConfigInfo4Tag(dataId, group, tenant, tag);
            
            CacheItem ci = CACHE.get(groupKey);
            if (ci.getConfigCacheTags() != null) {
                ci.getConfigCacheTags().remove(tag);
                if (ci.getConfigCacheTags().isEmpty()) {
                    ci.clearConfigTags();
                }
            }
            
            DUMP_LOG.info("[remove-tag-ok] remove tag in local jvm cache,tag={},groupKey={} ", tag, groupKey);
            
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, tag));
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
     * Update Beta md5 value.
     *
     * @param groupKey       groupKey string value.
     * @param md5Utf8        md5UTF8 string value.
     * @param ips4Beta       ips4Beta List.
     * @param lastModifiedTs lastModifiedTs long value.
     */
    public static void updateBetaMd5(String groupKey, String md5Utf8, List<String> ips4Beta, long lastModifiedTs,
            String encryptedDataKey4Beta) {
        CacheItem cache = makeSure(groupKey, null);
        cache.initBetaCacheIfEmpty();
        String betaMd5Utf8 = cache.getConfigCacheBeta().getMd5(ENCODE_UTF8);
        if (betaMd5Utf8 == null || !betaMd5Utf8.equals(md5Utf8) || !CollectionUtils.isListEqual(ips4Beta,
                cache.ips4Beta)) {
            cache.isBeta = true;
            cache.ips4Beta = ips4Beta;
            cache.getConfigCacheBeta().setMd5Utf8(md5Utf8);
            cache.getConfigCacheBeta().setLastModifiedTs(lastModifiedTs);
            cache.getConfigCacheBeta().setEncryptedDataKey(encryptedDataKey4Beta);
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, true, ips4Beta));
        }
    }
    
    /**
     * Update tag md5 value.
     *
     * @param groupKey       groupKey string value.
     * @param tag            tag string value.
     * @param md5Utf8        md5UTF8 string value.
     * @param lastModifiedTs lastModifiedTs long value.
     */
    public static void updateTagMd5(String groupKey, String tag, String md5Utf8, long lastModifiedTs,
            String encryptedDataKey4Tag) {
        CacheItem cache = makeSure(groupKey, null);
        cache.initConfigTagsIfEmpty(tag);
        ConfigCache configCache = cache.getConfigCacheTags().get(tag);
        configCache.setMd5Utf8(md5Utf8);
        configCache.setLastModifiedTs(lastModifiedTs);
        configCache.setEncryptedDataKey(encryptedDataKey4Tag);
        NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, tag));
    }
    
    /**
     * Get and return content md5 value from cache. Empty string represents no data.
     */
    public static String getContentMd5(String groupKey) {
        return getContentMd5(groupKey, "", "");
    }
    
    public static String getContentMd5(String groupKey, String ip, String tag) {
        CacheItem item = CACHE.get(groupKey);
        if (item != null && item.isBeta && item.ips4Beta != null && item.ips4Beta.contains(ip)
                && item.getConfigCacheBeta() != null) {
            return item.getConfigCacheBeta().getMd5(ENCODE_UTF8);
        }
        
        if (item != null && StringUtils.isNotBlank(tag) && item.getConfigCacheTags() != null
                && item.getConfigCacheTags().containsKey(tag)) {
            return item.getConfigCacheTags().get(tag).getMd5(ENCODE_UTF8);
        }
        
        if (item != null && item.isBatch && item.delimiter >= InternetAddressUtil.ipToInt(ip)
                && item.getConfigCacheBatch() != null) {
            return item.getConfigCacheBatch().getMd5(ENCODE_UTF8);
        }
        
        return (null != item) ? item.getConfigCache().getMd5(ENCODE_UTF8) : Constants.NULL;
    }
    
    /**
     * Get and return beta md5 value from cache. Empty string represents no data.
     */
    public static String getContentBetaMd5(String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        
        if (item == null || item.getConfigCacheBeta() == null) {
            return Constants.NULL;
        }
        return item.getConfigCacheBeta().getMd5(ENCODE_UTF8);
    }
    
    /**
     * Get and return tag md5 value from cache. Empty string represents no data.
     *
     * @param groupKey groupKey string value.
     * @param tag      tag string value.
     * @return Content Tag Md5 value.
     */
    public static String getContentTagMd5(String groupKey, String tag) {
        CacheItem item = CACHE.get(groupKey);
        if (item == null || item.getConfigCacheTags() == null || !item.getConfigCacheTags().containsKey(tag)) {
            return Constants.NULL;
        }
        return item.getConfigCacheTags().get(tag).getMd5(ENCODE_UTF8);
    }
    
    /**
     * Get and return beta ip list.
     *
     * @param groupKey groupKey string value.
     * @return list beta ips.
     */
    public static List<String> getBetaIps(String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        return (null != item) ? item.getIps4Beta() : Collections.emptyList();
    }
    
    public static long getBetaLastModifiedTs(String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        return (null != item && item.getConfigCacheBeta() != null) ? item.getConfigCacheBeta().getLastModifiedTs() : 0L;
    }
    
    public static long getTagLastModifiedTs(String groupKey, String tag) {
        CacheItem item = CACHE.get(groupKey);
        if (item.getConfigCacheTags() == null || !item.getConfigCacheTags().containsKey(tag)) {
            return 0;
        }
        ConfigCache configCacheTag = item.getConfigCacheTags().get(tag);
        
        return (null != configCacheTag) ? configCacheTag.getLastModifiedTs() : 0;
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
     * update tag timestamp.
     *
     * @param groupKey       groupKey.
     * @param tag            tag.
     * @param lastModifiedTs lastModifiedTs.
     */
    public static void updateTagTimeStamp(String groupKey, String tag, long lastModifiedTs) {
        CacheItem cache = makeSure(groupKey, null);
        cache.initConfigTagsIfEmpty(tag);
        cache.getConfigCacheTags().get(tag).setLastModifiedTs(lastModifiedTs);
        
    }
    
    public static boolean isUptodate(String groupKey, String md5) {
        String serverMd5 = ConfigCacheService.getContentMd5(groupKey);
        return StringUtils.equals(md5, serverMd5);
    }
    
    public static boolean isUptodate(String groupKey, String md5, String ip, String tag) {
        String serverMd5 = ConfigCacheService.getContentMd5(groupKey, ip, tag);
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
    public static void updateTimeStamp(String groupKey, long lastModifiedTs, String encryptedDataKey) {
        CacheItem cache = makeSure(groupKey, encryptedDataKey);
        cache.getConfigCache().setLastModifiedTs(lastModifiedTs);
    }
    
    /**
     * update beta ip list.
     *
     * @param groupKey       groupKey.
     * @param ips4Beta       ips4Beta.
     * @param lastModifiedTs lastModifiedTs.
     */
    private static void updateBetaIpList(String groupKey, List<String> ips4Beta, long lastModifiedTs) {
        CacheItem cache = makeSure(groupKey, null);
        cache.initBetaCacheIfEmpty();
        cache.setBeta(true);
        cache.setIps4Beta(ips4Beta);
        cache.getConfigCacheBeta().setLastModifiedTs(lastModifiedTs);
        NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, true, ips4Beta));
        
    }
    
    /**
     * update beta lastModifiedTs.
     *
     * @param groupKey       groupKey.
     * @param lastModifiedTs lastModifiedTs.
     */
    private static void updateBetaTimeStamp(String groupKey, long lastModifiedTs) {
        CacheItem cache = makeSure(groupKey, null);
        cache.initBetaCacheIfEmpty();
        cache.getConfigCacheBeta().setLastModifiedTs(lastModifiedTs);
        
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

