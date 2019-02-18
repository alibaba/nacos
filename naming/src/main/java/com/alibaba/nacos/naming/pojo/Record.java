package com.alibaba.nacos.naming.pojo;

/**
 * Record to transfer and store in Nacos cluster
 *
 * @author nkorange
 * @since 1.0.0
 */
public interface Record {
    /**
     * get the checksum of this record, usually for record comparison
     *
     * @return checksum of record
     */
    String getChecksum();
}
