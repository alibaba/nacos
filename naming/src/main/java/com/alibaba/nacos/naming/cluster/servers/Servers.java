package com.alibaba.nacos.naming.cluster.servers;

import com.alibaba.nacos.common.util.Md5Utils;
import com.alibaba.nacos.naming.pojo.Record;

import java.util.List;

/**
 * @author XCXCXCXCX
 * @since 1.0
 */
public class Servers implements Record{

    private List<String> clusterHosts;

    public Servers(List<String> clusterHosts) {
        this.clusterHosts = clusterHosts;
    }

    public List<String> getClusterHosts() {
        return clusterHosts;
    }

    public void setClusterHosts(List<String> clusterHosts) {
        this.clusterHosts = clusterHosts;
    }

    /**
     * get the checksum of this record, usually for record comparison
     *
     * @return checksum of record
     */
    @Override
    public String getChecksum() {
        return Md5Utils.getMD5(clusterHosts.toString(), "UTF-8");
    }
}
