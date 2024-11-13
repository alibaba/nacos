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

package com.alibaba.nacos.config.server.service.dump.disk;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;
import static com.alibaba.nacos.config.server.constant.Constants.NULL;

/**
 * config rocks db disk service.
 *
 * @author shiyiyue
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class ConfigRocksDbDiskService implements ConfigDiskService {
    
    private static final String ROCKSDB_DATA = File.separator + "rocksdata" + File.separator;
    
    private static final String BASE_DIR = ROCKSDB_DATA + "config-data";
    
    private static final String GRAY_DIR = ROCKSDB_DATA + "gray-data";
    
    private static final long DEFAULT_WRITE_BUFFER_MB = 32;
    
    Map<String, RocksDB> rocksDbMap = new HashMap<>();
    
    private void createDirIfNotExist(String dir) {
        File roskDataDir = new File(EnvUtil.getNacosHome(), "rocksdata");
        if (!roskDataDir.exists()) {
            roskDataDir.mkdirs();
        }
        File baseDir = new File(EnvUtil.getNacosHome(), dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }
    
    private void deleteDirIfExist(String dir) {
        File rockskDataDir = new File(EnvUtil.getNacosHome(), "rocksdata");
        if (!rockskDataDir.exists()) {
            return;
        }
        File baseDir = new File(EnvUtil.getNacosHome(), dir);
        if (baseDir.exists()) {
            baseDir.delete();
        }
    }
    
    public ConfigRocksDbDiskService() {
        createDirIfNotExist(BASE_DIR);
        createDirIfNotExist(GRAY_DIR);
        
    }
    
    private byte[] getKeyByte(String dataId, String group, String tenant, String tag) throws IOException {
        String[] keys = new String[] {dataId, group, tenant, tag};
        return getKeyByte(keys);
    }
    
    private byte[] getKeyByte(String... keys) throws IOException {
        if (keys == null || keys.length == 0) {
            return NULL.getBytes(ENCODE_UTF8);
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                key = "";
            }
            urlEncode(key, stringBuilder);
            stringBuilder.append("+");
        }
        return stringBuilder.toString().getBytes(ENCODE_UTF8);
    }
    
    /**
     * + -> %2B % -> %25.
     */
    private static void urlEncode(String str, StringBuilder sb) {
        for (int idx = 0; idx < str.length(); ++idx) {
            char c = str.charAt(idx);
            if ('+' == c) {
                sb.append("%2B");
            } else if ('%' == c) {
                sb.append("%25");
            } else {
                sb.append(c);
            }
        }
    }
    
    /**
     * save config to disk.
     */
    public void saveToDiskInner(String type, String dataId, String group, String tenant, String tag, String content)
            throws IOException {
        try {
            initAndGetDB(type).put(getKeyByte(dataId, group, tenant, tag), content.getBytes(ENCODE_UTF8));
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }
    
    /**
     * save config to disk.
     */
    public void saveToDiskInner(String type, String dataId, String group, String tenant, String content)
            throws IOException {
        saveToDiskInner(type, dataId, group, tenant, null, content);
    }
    
    /**
     * save config to disk.
     */
    public void saveGrayToDiskInner(String type, String dataId, String group, String tenant, String grayName,
            String content) throws IOException {
        try {
            initAndGetDB(type).put(getKeyByte(dataId, group, tenant, grayName), content.getBytes(ENCODE_UTF8));
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }
    
    /**
     * Save configuration information to disk.
     */
    public void saveToDisk(String dataId, String group, String tenant, String content) throws IOException {
        saveToDiskInner(BASE_DIR, dataId, group, tenant, content);
    }
    
    /**
     * Save tag information to disk.
     */
    public void saveGrayToDisk(String dataId, String group, String tenant, String grayName, String content)
            throws IOException {
        saveGrayToDiskInner(GRAY_DIR, dataId, group, tenant, grayName, content);
        
    }
    
    /**
     * Deletes configuration files on disk.
     */
    public void removeConfigInfo(String dataId, String group, String tenant) {
        removeContentInner(BASE_DIR, dataId, group, tenant, null);
    }
    
    /**
     * Deletes gray configuration files on disk.
     */
    public void removeConfigInfo4Gray(String dataId, String group, String tenant, String grayName) {
        removeGrayInner(GRAY_DIR, dataId, group, tenant, grayName);
        
    }
    
    private String byte2String(byte[] bytes) throws IOException {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, ENCODE_UTF8);
    }
    
    RocksDB initAndGetDB(String dir) throws IOException, RocksDBException {
        if (rocksDbMap.containsKey(dir)) {
            return rocksDbMap.get(dir);
        } else {
            synchronized (this) {
                if (rocksDbMap.containsKey(dir)) {
                    return rocksDbMap.get(dir);
                }
                createDirIfEmpty(EnvUtil.getNacosHome() + dir);
                rocksDbMap.put(dir, RocksDB.open(createOptions(dir), EnvUtil.getNacosHome() + dir));
                return rocksDbMap.get(dir);
            }
            
        }
    }
    
    private void createDirIfEmpty(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            file.mkdirs();
        }
    }
    
    private String getContentInner(String type, String dataId, String group, String tenant) throws IOException {
        byte[] bytes = null;
        try {
            bytes = initAndGetDB(type).get(getKeyByte(dataId, group, tenant, null));
            String string = byte2String(bytes);
            return string;
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }
    
    private String getGrayInner(String type, String dataId, String group, String tenant, String grayName)
            throws IOException {
        byte[] bytes = null;
        try {
            bytes = initAndGetDB(type).get(getKeyByte(dataId, group, tenant, grayName));
            return byte2String(bytes);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }
    
    private void removeContentInner(String type, String dataId, String group, String tenant, String tag) {
        try {
            initAndGetDB(type).delete(getKeyByte(dataId, group, tenant, tag));
        } catch (Exception e) {
            LogUtil.DEFAULT_LOG.warn("Remove dir=[{}] config fail,dataId={},group={},tenant={},error={}", type, dataId,
                    group, tenant, e.getCause());
        }
    }
    
    private void removeGrayInner(String type, String dataId, String group, String tenant, String grayName) {
        try {
            initAndGetDB(type).delete(getKeyByte(dataId, group, tenant, grayName));
        } catch (Exception e) {
            LogUtil.DEFAULT_LOG.warn("Remove dir=[{}] config fail,dataId={},group={},tenant={},error={}", type, dataId,
                    group, tenant, e.getCause());
        }
    }
    
    /**
     * Returns the path of the gray content cache file in server.
     */
    public String getGrayContent(String dataId, String group, String tenant, String grayName) throws IOException {
        return getGrayInner(GRAY_DIR, dataId, group, tenant, grayName);
    }
    
    public String getContent(String dataId, String group, String tenant) throws IOException {
        return getContentInner(BASE_DIR, dataId, group, tenant);
    }
    
    public String getLocalConfigMd5(String dataId, String group, String tenant, String encode) throws IOException {
        return MD5Utils.md5Hex(getContentInner(BASE_DIR, dataId, group, tenant), encode);
    }
    
    Options createOptions(String dir) {
        DBOptions dbOptions = new DBOptions();
        dbOptions.setMaxBackgroundJobs(Runtime.getRuntime().availableProcessors());
        Options options = new Options(dbOptions, createColumnFamilyOptions(dir));
        options.setCreateIfMissing(true);
        return options;
    }
    
    ColumnFamilyOptions createColumnFamilyOptions(String dir) {
        ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
        BlockBasedTableConfig tableFormatConfig = new BlockBasedTableConfig();
        columnFamilyOptions.setTableFormatConfig(tableFormatConfig);
        //set more write buffer size to formal config-data, reduce flush to sst file frequency.
        columnFamilyOptions.setWriteBufferSize(getSuitFormalCacheSizeMB(dir) * 1024 * 1024);
        //once a stt file is flushed, compact it immediately to avoid too many sst file which will result in read latency.
        columnFamilyOptions.setLevel0FileNumCompactionTrigger(1);
        return columnFamilyOptions;
    }
    
    /**
     * get suit formal buffer size.
     *
     * @return
     */
    @SuppressWarnings("PMD.UndefineMagicConstantRule")
    private long getSuitFormalCacheSizeMB(String dir) {
        
        boolean formal = BASE_DIR.equals(dir);
        long maxHeapSizeMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        
        if (formal) {
            long formalWriteBufferSizeMB = 0;
            
            if (maxHeapSizeMB < 8 * 1024) {
                formalWriteBufferSizeMB = 32;
            } else if (maxHeapSizeMB < 16 * 1024) {
                formalWriteBufferSizeMB = 64;
            } else {
                formalWriteBufferSizeMB = 256;
            }
            LogUtil.DEFAULT_LOG.info("init formal rocksdb write buffer size {}M for dir {}, maxHeapSize={}M",
                    formalWriteBufferSizeMB, dir, maxHeapSizeMB);
            return formalWriteBufferSizeMB;
        } else {
            LogUtil.DEFAULT_LOG.info("init default rocksdb write buffer size {}M for dir {}, maxHeapSize={}M",
                    DEFAULT_WRITE_BUFFER_MB, dir, maxHeapSizeMB);
            return DEFAULT_WRITE_BUFFER_MB;
        }
        
    }
    
    /**
     * Clear all config file.
     */
    public void clearAll() {
        try {
            if (rocksDbMap.containsKey(BASE_DIR)) {
                rocksDbMap.get(BASE_DIR).close();
                RocksDB.destroyDB(EnvUtil.getNacosHome() + BASE_DIR, new Options());
            }
            deleteDirIfExist(BASE_DIR);
            LogUtil.DEFAULT_LOG.info("clear all config-info success.");
        } catch (RocksDBException e) {
            LogUtil.DEFAULT_LOG.warn("clear all config-info failed.", e);
        }
    }
    
    /**
     * Clear all gray config file.
     */
    public void clearAllGray() {
        
        try {
            if (rocksDbMap.containsKey(GRAY_DIR)) {
                rocksDbMap.get(GRAY_DIR).close();
                RocksDB.destroyDB(EnvUtil.getNacosHome() + GRAY_DIR, new Options());
            }
            deleteDirIfExist(GRAY_DIR);
            LogUtil.DEFAULT_LOG.info("clear all config-info-gray success.");
        } catch (RocksDBException e) {
            LogUtil.DEFAULT_LOG.warn("clear all config-info-gray failed.", e);
        }
    }
    
}
