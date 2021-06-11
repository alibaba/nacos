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
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.DiskUtil;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.config.server.utils.LogUtil.DUMP_LOG;
import static com.alibaba.nacos.config.server.utils.LogUtil.FATAL_LOG;
import static com.alibaba.nacos.config.server.utils.LogUtil.DEFAULT_LOG;

/**
 * Config service.
 *
 * @author Nacos
 */
public class ConfigCacheService {

    static final Logger LOGGER = LoggerFactory.getLogger(ConfigCacheService.class);

    private static final String NO_SPACE_CN = "设备上没有空间";

    private static final String NO_SPACE_EN = "No space left on device";

    private static final String DISK_QUATA_CN = "超出磁盘限额";

    private static final String DISK_QUATA_EN = "Disk quota exceeded";

    /**
     * groupKey -> cacheItem.
     */
    private static final ConcurrentHashMap<String, CacheItem> CACHE = new ConcurrentHashMap<String, CacheItem>();
    
    @Autowired
    private static PersistService persistService;
    
    public static int groupCount() {
        return CACHE.size();
    }
    
    public static boolean hasGroupKey(String groupKey) {
        return CACHE.containsKey(groupKey);
    }
    
    /**
     * Save config file and update md5 value in cache.
     *
     * @param dataId         dataId string value.
     * @param group          group string value.
     * @param tenant         tenant string value.
     * @param content        content string value.
     * @param lastModifiedTs lastModifiedTs.
     * @param type           file type.
     * @return dumpChange success or not.
     */
    public static boolean dump(String dataId, String group, String tenant, String content, long lastModifiedTs,
            String type) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        CacheItem ci = makeSure(groupKey);
        ci.setType(type);
        final int lockResult = tryWriteLock(groupKey);
        assert (lockResult != 0);
        
        if (lockResult < 0) {
            DUMP_LOG.warn("[dump-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
            
            if (md5.equals(ConfigCacheService.getContentMd5(groupKey))) {
                DUMP_LOG.warn("[dump-ignore] ignore to save cache file. groupKey={}, md5={}, lastModifiedOld={}, "
                                + "lastModifiedNew={}", groupKey, md5, ConfigCacheService.getLastModifiedTs(groupKey),
                        lastModifiedTs);
            } else if (!PropertyUtil.isDirectRead()) {
                DiskUtil.saveToDisk(dataId, group, tenant, content);
            }
            updateMd5(groupKey, md5, lastModifiedTs);
            return true;
        } catch (IOException ioe) {
            DUMP_LOG.error("[dump-exception] save disk error. " + groupKey + ", " + ioe.toString(), ioe);
            if (ioe.getMessage() != null) {
                String errMsg = ioe.getMessage();
                if (NO_SPACE_CN.equals(errMsg) || NO_SPACE_EN.equals(errMsg) || errMsg.contains(DISK_QUATA_CN) || errMsg
                        .contains(DISK_QUATA_EN)) {
                    // Protect from disk full.
                    FATAL_LOG.error("磁盘满自杀退出", ioe);
                    System.exit(0);
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
     * @param dataId         dataId string value.
     * @param group          group string value.
     * @param tenant         tenant string value.
     * @param content        content string value.
     * @param lastModifiedTs lastModifiedTs.
     * @param betaIps        betaIps string value.
     * @return dumpChange success or not.
     */
    public static boolean dumpBeta(String dataId, String group, String tenant, String content, long lastModifiedTs,
            String betaIps) {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        
        makeSure(groupKey);
        final int lockResult = tryWriteLock(groupKey);
        assert (lockResult != 0);
        
        if (lockResult < 0) {
            DUMP_LOG.warn("[dump-beta-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
            if (md5.equals(ConfigCacheService.getContentBetaMd5(groupKey))) {
                DUMP_LOG.warn("[dump-beta-ignore] ignore to save cache file. groupKey={}, md5={}, lastModifiedOld={}, "
                                + "lastModifiedNew={}", groupKey, md5, ConfigCacheService.getLastModifiedTs(groupKey),
                        lastModifiedTs);
            } else if (!PropertyUtil.isDirectRead()) {
                DiskUtil.saveBetaToDisk(dataId, group, tenant, content);
            }
            String[] betaIpsArr = betaIps.split(",");
            
            updateBetaMd5(groupKey, md5, Arrays.asList(betaIpsArr), lastModifiedTs);
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
            long lastModifiedTs) {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        
        makeSure(groupKey);
        final int lockResult = tryWriteLock(groupKey);
        assert (lockResult != 0);
        
        if (lockResult < 0) {
            DUMP_LOG.warn("[dump-tag-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
            if (md5.equals(ConfigCacheService.getContentTagMd5(groupKey, tag))) {
                DUMP_LOG.warn("[dump-tag-ignore] ignore to save cache file. groupKey={}, md5={}, lastModifiedOld={}, "
                                + "lastModifiedNew={}", groupKey, md5, ConfigCacheService.getLastModifiedTs(groupKey),
                        lastModifiedTs);
            } else if (!PropertyUtil.isDirectRead()) {
                DiskUtil.saveTagToDisk(dataId, group, tenant, tag, content);
            }
            
            updateTagMd5(groupKey, tag, md5, lastModifiedTs);
            return true;
        } catch (IOException ioe) {
            DUMP_LOG.error("[dump-tag-exception] save disk error. " + groupKey + ", " + ioe.toString(), ioe);
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
     * @return dumpChange success or not.
     */
    public static boolean dumpChange(String dataId, String group, String tenant, String content, long lastModifiedTs) {
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        
        makeSure(groupKey);
        final int lockResult = tryWriteLock(groupKey);
        assert (lockResult != 0);
        
        if (lockResult < 0) {
            DUMP_LOG.warn("[dump-error] write lock failed. {}", groupKey);
            return false;
        }
        
        try {
            final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
            if (!PropertyUtil.isDirectRead()) {
                String localMd5 = DiskUtil.getLocalConfigMd5(dataId, group, tenant);
                if (md5.equals(localMd5)) {
                    DUMP_LOG.warn("[dump-ignore] ignore to save cache file. groupKey={}, md5={}, lastModifiedOld={}, "
                                    + "lastModifiedNew={}", groupKey, md5, ConfigCacheService.getLastModifiedTs(groupKey),
                            lastModifiedTs);
                } else {
                    DiskUtil.saveToDisk(dataId, group, tenant, content);
                }
            }
            updateMd5(groupKey, md5, lastModifiedTs);
            return true;
        } catch (IOException ioe) {
            DUMP_LOG.error("[dump-exception] save disk error. " + groupKey + ", " + ioe.toString(), ioe);
            return false;
        } finally {
            releaseWriteLock(groupKey);
        }
    }
    
    /**
     * Reload config.
     */
    public static void reloadConfig() {
        String aggreds = null;
        try {
            if (PropertyUtil.isEmbeddedStorage()) {
                ConfigInfoBase config = persistService
                        .findConfigInfoBase(AggrWhitelist.AGGRIDS_METADATA, "DEFAULT_GROUP");
                if (config != null) {
                    aggreds = config.getContent();
                }
            } else {
                aggreds = DiskUtil.getConfig(AggrWhitelist.AGGRIDS_METADATA, "DEFAULT_GROUP", StringUtils.EMPTY);
            }
            if (aggreds != null) {
                AggrWhitelist.load(aggreds);
            }
        } catch (IOException e) {
            DUMP_LOG.error("reload fail:" + AggrWhitelist.AGGRIDS_METADATA, e);
        }
        
        String clientIpWhitelist = null;
        try {
            if (PropertyUtil.isEmbeddedStorage()) {
                ConfigInfoBase config = persistService
                        .findConfigInfoBase(ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA, "DEFAULT_GROUP");
                if (config != null) {
                    clientIpWhitelist = config.getContent();
                }
            } else {
                clientIpWhitelist = DiskUtil
                        .getConfig(ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA, "DEFAULT_GROUP", StringUtils.EMPTY);
            }
            if (clientIpWhitelist != null) {
                ClientIpWhiteList.load(clientIpWhitelist);
            }
        } catch (IOException e) {
            DUMP_LOG.error("reload fail:" + ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA, e);
        }
        
        String switchContent = null;
        try {
            if (PropertyUtil.isEmbeddedStorage()) {
                ConfigInfoBase config = persistService
                        .findConfigInfoBase(SwitchService.SWITCH_META_DATAID, "DEFAULT_GROUP");
                if (config != null) {
                    switchContent = config.getContent();
                }
            } else {
                switchContent = DiskUtil
                        .getConfig(SwitchService.SWITCH_META_DATAID, "DEFAULT_GROUP", StringUtils.EMPTY);
            }
            if (switchContent != null) {
                SwitchService.load(switchContent);
            }
        } catch (IOException e) {
            DUMP_LOG.error("reload fail:" + SwitchService.SWITCH_META_DATAID, e);
        }
    }
    
    /**
     * Check md5.
     *
     * @return return diff result list.
     */
    public static List<String> checkMd5() {
        List<String> diffList = new ArrayList<String>();
        long startTime = System.currentTimeMillis();
        for (Entry<String/* groupKey */, CacheItem> entry : CACHE.entrySet()) {
            String groupKey = entry.getKey();
            String[] dg = GroupKey.parseKey(groupKey);
            String dataId = dg[0];
            String group = dg[1];
            String tenant = dg[2];
            try {
                String loacalMd5 = DiskUtil.getLocalConfigMd5(dataId, group, tenant);
                if (!entry.getValue().md5.equals(loacalMd5)) {
                    DEFAULT_LOG.warn("[md5-different] dataId:{},group:{}", dataId, group);
                    diffList.add(groupKey);
                }
            } catch (IOException e) {
                DEFAULT_LOG.error("getLocalConfigMd5 fail,dataId:{},group:{}", dataId, group);
            }
        }
        long endTime = System.currentTimeMillis();
        DEFAULT_LOG.warn("checkMd5 cost:{}; diffCount:{}", endTime - startTime, diffList.size());
        return diffList;
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
            if (!PropertyUtil.isDirectRead()) {
                DiskUtil.removeConfigInfo(dataId, group, tenant);
            }
            CACHE.remove(groupKey);
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
            if (!PropertyUtil.isDirectRead()) {
                DiskUtil.removeConfigInfo4Beta(dataId, group, tenant);
            }
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, true, CACHE.get(groupKey).getIps4Beta()));
            CACHE.get(groupKey).setBeta(false);
            CACHE.get(groupKey).setIps4Beta(null);
            CACHE.get(groupKey).setMd54Beta(Constants.NULL);
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
            if (!PropertyUtil.isDirectRead()) {
                DiskUtil.removeConfigInfo4Tag(dataId, group, tenant, tag);
            }
            
            CacheItem ci = CACHE.get(groupKey);
            ci.tagMd5.remove(tag);
            ci.tagLastModifiedTs.remove(tag);
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, false, null, tag));
            return true;
        } finally {
            releaseWriteLock(groupKey);
        }
    }
    
    /**
     * Update md5 value.
     *
     * @param groupKey       groupKey string value.
     * @param md5            md5 string value.
     * @param lastModifiedTs lastModifiedTs long value.
     */
    public static void updateMd5(String groupKey, String md5, long lastModifiedTs) {
        CacheItem cache = makeSure(groupKey);
        if (cache.md5 == null || !cache.md5.equals(md5)) {
            cache.md5 = md5;
            cache.lastModifiedTs = lastModifiedTs;
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey));
        }
    }
    
    /**
     * Update Beta md5 value.
     *
     * @param groupKey       groupKey string value.
     * @param md5            md5 string value.
     * @param ips4Beta       ips4Beta List.
     * @param lastModifiedTs lastModifiedTs long value.
     */
    public static void updateBetaMd5(String groupKey, String md5, List<String> ips4Beta, long lastModifiedTs) {
        CacheItem cache = makeSure(groupKey);
        if (cache.md54Beta == null || !cache.md54Beta.equals(md5)) {
            cache.isBeta = true;
            cache.md54Beta = md5;
            cache.lastModifiedTs4Beta = lastModifiedTs;
            cache.ips4Beta = ips4Beta;
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, true, ips4Beta));
        }
    }
    
    /**
     * Update tag md5 value.
     *
     * @param groupKey       groupKey string value.
     * @param tag            tag string value.
     * @param md5            md5 string value.
     * @param lastModifiedTs lastModifiedTs long value.
     */
    public static void updateTagMd5(String groupKey, String tag, String md5, long lastModifiedTs) {
        CacheItem cache = makeSure(groupKey);
        if (cache.tagMd5 == null) {
            Map<String, String> tagMd5Tmp = new HashMap<String, String>(1);
            tagMd5Tmp.put(tag, md5);
            cache.tagMd5 = tagMd5Tmp;
            if (cache.tagLastModifiedTs == null) {
                Map<String, Long> tagLastModifiedTsTmp = new HashMap<String, Long>(1);
                tagLastModifiedTsTmp.put(tag, lastModifiedTs);
                cache.tagLastModifiedTs = tagLastModifiedTsTmp;
            } else {
                cache.tagLastModifiedTs.put(tag, lastModifiedTs);
            }
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, false, null, tag));
            return;
        }
        if (cache.tagMd5.get(tag) == null || !cache.tagMd5.get(tag).equals(md5)) {
            cache.tagMd5.put(tag, md5);
            cache.tagLastModifiedTs.put(tag, lastModifiedTs);
            NotifyCenter.publishEvent(new LocalDataChangeEvent(groupKey, false, null, tag));
        }
    }
    
    /**
     * Get and return content md5 value from cache. Empty string represents no data.
     */
    public static String getContentMd5(String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        return (null != item) ? item.md5 : Constants.NULL;
    }
    
    public static String getContentMd5(String groupKey, String ip, String tag) {
        CacheItem item = CACHE.get(groupKey);
        if (item != null && item.isBeta) {
            if (item.ips4Beta.contains(ip)) {
                return item.md54Beta;
            }
        }
        if (item != null && item.tagMd5 != null && item.tagMd5.size() > 0) {
            if (StringUtils.isNotBlank(tag) && item.tagMd5.containsKey(tag)) {
                return item.tagMd5.get(tag);
            }
        }
        return (null != item) ? item.md5 : Constants.NULL;
    }
    
    /**
     * Get and return beta md5 value from cache. Empty string represents no data.
     */
    public static String getContentBetaMd5(String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        return (null != item) ? item.md54Beta : Constants.NULL;
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
        if (item == null) {
            return Constants.NULL;
        }
        if (item.tagMd5 == null) {
            return Constants.NULL;
        }
        return item.tagMd5.get(tag);
    }
    
    /**
     * Get and return beta ip list.
     *
     * @param groupKey groupKey string value.
     * @return list beta ips.
     */
    public static List<String> getBetaIps(String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        return (null != item) ? item.getIps4Beta() : Collections.<String>emptyList();
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
        return (null != item) ? item.lastModifiedTs : 0L;
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
        int result = (null == groupItem) ? 0 : (groupItem.rwLock.tryReadLock() ? 1 : -1);
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
            item.rwLock.releaseReadLock();
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
        int result = (null == groupItem) ? 0 : (groupItem.rwLock.tryWriteLock() ? 1 : -1);
        if (result < 0) {
            DEFAULT_LOG.warn("[write-lock] failed, {}, {}", result, groupKey);
        }
        return result;
    }
    
    static void releaseWriteLock(String groupKey) {
        CacheItem groupItem = CACHE.get(groupKey);
        if (null != groupItem) {
            groupItem.rwLock.releaseWriteLock();
        }
    }
    
    static CacheItem makeSure(final String groupKey) {
        CacheItem item = CACHE.get(groupKey);
        if (null != item) {
            return item;
        }
        CacheItem tmp = new CacheItem(groupKey);
        item = CACHE.putIfAbsent(groupKey, tmp);
        return (null == item) ? tmp : item;
    }
}

