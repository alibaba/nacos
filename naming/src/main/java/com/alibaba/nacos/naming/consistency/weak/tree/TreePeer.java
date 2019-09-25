package com.alibaba.nacos.naming.consistency.weak.tree;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * @author satjd
 */
public class TreePeer implements Comparable<TreePeer>{
    public String ip;
    public int port;
    public String key;


    // todo hashcode , equals , compareTo
    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof TreePeer)) {
            return false;
        }

        TreePeer other = (TreePeer) obj;

        return StringUtils.equals(key, other.key);
    }

    @Override
    public int compareTo(TreePeer o) {
        return this.key.compareTo(o.key);
    }

    @Override
    public String toString() {
        return "TreePeer-" + ip + ":" + port;
    }
}
