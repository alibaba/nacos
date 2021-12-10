package com.alibaba.nacos.naming.pojo;

import com.fasterxml.jackson.databind.node.ArrayNode;


/**
 * Instance Updated Info
 *
 * @author Steafan
 * @since 2.0.3
 */
public class InstanceUpdatedInfo {

    private ArrayNode ipArray;

    public void setIpArray(ArrayNode ipArray) {
        this.ipArray = ipArray;
    }

    public ArrayNode getIpArray() {
        return ipArray;
    }

    @Override
    public String toString() {
        return "InstanceUpdatedInfo{" +
                "ipArray=" + ipArray +
                '}';
    }
}
