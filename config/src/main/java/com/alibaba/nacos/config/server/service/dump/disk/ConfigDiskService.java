package com.alibaba.nacos.config.server.service.dump.disk;

import java.io.IOException;

/**
 * zunfei.lzf
 */
public interface ConfigDiskService {
    
    /**
     * Save configuration information to disk.
     */
    void saveToDisk(String dataId, String group, String tenant, String content) throws IOException;
    
    /**
     * Save beta information to disk.
     */
    void saveBetaToDisk(String dataId, String group, String tenant, String content) throws IOException;
    
    /**
     * Save batch information to disk.
     */
    void saveBatchToDisk(String dataId, String group, String tenant, String content) throws IOException;
    
    /**
     * Save tag information to disk.
     */
    void saveTagToDisk(String dataId, String group, String tenant, String tag, String content) throws IOException;
    
    /**
     * Deletes configuration files on disk.
     */
    void removeConfigInfo(String dataId, String group, String tenant);
    
    /**
     * Deletes beta configuration files on disk.
     */
    void removeConfigInfo4Beta(String dataId, String group, String tenant);
    
    /**
     * Deletes batch config file on disk.
     */
    void removeConfigInfo4Batch(String dataId, String group, String tenant);
    
    /**
     * Deletes tag configuration files on disk.
     */
    void removeConfigInfo4Tag(String dataId, String group, String tenant, String tag);
    
    /**
     * Returns the content of the  cache file in server.
     *
     * @param dataId
     * @param group
     * @param tenant
     * @return content, null if not exist.
     * @throws IOException
     */
    String getContent(String dataId, String group, String tenant) throws IOException;
    
    /**
     * Returns the beta content of cache file in server.
     */
    String getBetaContent(String dataId, String group, String tenant) throws IOException;
    
    /**
     * get batch content.
     *
     * @param dataId
     * @param group
     * @param tenant
     * @return batch content, null if not exist.
     * @throws IOException
     */
    String getBatchContent(String dataId, String group, String tenant) throws IOException;
    
    
    /**
     * Returns the path of the tag cache file in server.
     */
    String getTagContent(String dataId, String group, String tenant, String tag) throws IOException;
    
    /**
     * get the md5 of config with encode.
     *
     * @param dataId
     * @param group
     * @param tenant
     * @param encode
     * @return
     * @throws IOException
     */
    String getLocalConfigMd5(String dataId, String group, String tenant, String encode) throws IOException;
    
    /**
     * Clear all config file.
     */
    void clearAll();
    
    /**
     * Clear all beta config file.
     */
    void clearAllBeta();
    
    /**
     * Clear all tag config file.
     */
    void clearAllTag();
    
    /**
     * Clear all batch config file.
     */
    void clearAllBatch();
    
}
