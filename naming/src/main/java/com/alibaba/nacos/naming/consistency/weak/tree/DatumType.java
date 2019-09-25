package com.alibaba.nacos.naming.consistency.weak.tree;

/**
 * @author satjd
 */

public enum DatumType {
    /**
     * Type of message for update datum
     */
    UPDATE,
    /**
     * Type of message for delete datum
     */
    DELETE,
    /**
     * Type of message for cluster management(topology change, treeN change etc.)
     */
    OPERATION,
}
