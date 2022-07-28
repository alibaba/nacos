package com.alibaba.nacos.plugin.datasource.mapper;

import com.alibaba.nacos.config.server.model.ConfigInfo;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * The ConfigInfo Mapper, providing access to ConfigInfo in the database.
 *
 * @author hyx
 **/
public interface ConfigMapper extends BaseMapper<ConfigInfo> {
    
    /**
     * Write to the main table, insert or update.
     *
     * @param srcIp             remote ip
     * @param srcUser           user
     * @param configInfo        config info
     * @param time              time
     * @param configAdvanceInfo advance info
     * @param notify            whether to push
     * @return Whether insert or update succeed
     */
    boolean insertOrUpdateConfigInfo(String srcIp, String srcUser, ConfigInfo configInfo,
            Timestamp time, Map<String, Object> configAdvanceInfo, boolean notify);
    
    /**
     * To get the all configInfo by dataId, group and tenant.
     *
     * @param dataId            data id
     * @param group             group
     * @param tenant            tenant
     * @return The all configInfo by dataId, group and tenant.
     */
    List<ConfigInfo> selectAll(String dataId, String group, String tenant);
    
    /**
     * To delete a configInfo.
     * @param dataId            data id
     * @param group             group
     * @param tenant            tenant
     * @param clientIp          remote ip
     * @param srcUser           user
     * @return The result of delete.
     */
    boolean delete(String dataId, String group, String tenant, String clientIp, String srcUser);
}
