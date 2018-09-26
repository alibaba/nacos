package com.alibaba.nacos.naming.pojo;

import java.io.Serializable;
import java.util.List;

/**
 * @author caogu.wyp
 * @version $Id: ClusterInfo.java, v 0.1 2018-09-17 上午11:36 caogu.wyp Exp $$
 */
public class ClusterInfo implements Serializable {

    private List<IpAddressInfo> hosts;

    /**
     * Getter method for property <tt>hosts</tt>.
     *
     * @return property value of hosts
     */
    public List<IpAddressInfo> getHosts() {
        return hosts;
    }

    /**
     * Setter method for property <tt>hosts </tt>.
     *
     * @param hosts value to be assigned to property hosts
     */
    public void setHosts(List<IpAddressInfo> hosts) {
        this.hosts = hosts;
    }
}
