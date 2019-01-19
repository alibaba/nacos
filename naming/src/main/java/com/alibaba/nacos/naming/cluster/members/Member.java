package com.alibaba.nacos.naming.cluster.members;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;

/**
 * Member node of Nacos cluster
 *
 * @author nkorange
 * @since 1.0.0
 */
public class Member {

    /**
     * IP of member
     */
    private String ip;

    /**
     * serving port of member.
     */
    private int servePort;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getServePort() {
        return servePort;
    }

    public void setServePort(int servePort) {
        this.servePort = servePort;
    }

    public String getKey() {
        return ip + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + servePort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Member member = (Member) o;
        return servePort == member.servePort && ip.equals(member.ip);
    }

    @Override
    public int hashCode() {
        int result = ip.hashCode();
        result = 31 * result + servePort;
        return result;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
