package com.alibaba.nacos.naming.cluster.servers;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;

/**
 * Member node of Nacos cluster
 *
 * @author nkorange
 * @since 1.0.0
 */
public class Server implements Comparable<Server> {

    /**
     * IP of member
     */
    private String ip;

    /**
     * serving port of member.
     */
    private int servePort;

    private String site = UtilsAndCommons.UNKNOWN_SITE;

    private int weight = 1;

    /**
     * additional weight, used to adjust manually
     */
    private int adWeight;

    private boolean alive = false;

    private long lastRefTime = 0L;

    private String lastRefTimeStr;

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

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getAdWeight() {
        return adWeight;
    }

    public void setAdWeight(int adWeight) {
        this.adWeight = adWeight;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public long getLastRefTime() {
        return lastRefTime;
    }

    public void setLastRefTime(long lastRefTime) {
        this.lastRefTime = lastRefTime;
    }

    public String getLastRefTimeStr() {
        return lastRefTimeStr;
    }

    public void setLastRefTimeStr(String lastRefTimeStr) {
        this.lastRefTimeStr = lastRefTimeStr;
    }

    public String getKey() {
        return ip + UtilsAndCommons.IP_PORT_SPLITER + servePort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Server server = (Server) o;
        return servePort == server.servePort && ip.equals(server.ip);
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

    @Override
    public int compareTo(Server server) {
        if (server == null) {
            return 1;
        }
        return this.getKey().compareTo(server.getKey());
    }
}
