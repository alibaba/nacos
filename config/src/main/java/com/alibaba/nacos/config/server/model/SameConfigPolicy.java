package com.alibaba.nacos.config.server.model;

/**
 * @author klw
 * @ClassName: SameConfigPolicy
 * @Description: processing policy of the same configuration
 * @date 2019/5/21 10:55
 */
public enum SameConfigPolicy {

    ABORT,
    SKIP,
    OVERWRITE;

}
