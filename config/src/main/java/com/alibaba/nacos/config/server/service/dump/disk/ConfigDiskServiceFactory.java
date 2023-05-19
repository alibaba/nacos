package com.alibaba.nacos.config.server.service.dump.disk;

/**
 * @author zunfei.lzf
 */
public class ConfigDiskServiceFactory {
    
    static ConfigDiskService configDiskService;
    
    private static final String TYPE_RAW_DISK = "rawdisk";
    
    private static final String TYPE_ROCKSDB = "rocksdb";
    
    /**
     * get disk service.
     *
     * @return
     */
    public static ConfigDiskService getInstance() {
        if (configDiskService == null) {
            synchronized (ConfigDiskServiceFactory.class) {
                if (configDiskService == null) {
                    String type = System.getProperty("config_disk_type", TYPE_RAW_DISK);
                    if (type.equalsIgnoreCase(TYPE_ROCKSDB)) {
                        configDiskService = new ConfigRocksDbDiskService();
                    } else {
                        configDiskService = new ConfigRawDiskService();
                    }
                }
                return configDiskService;
            }
        }
        return configDiskService;
    }
    
}
