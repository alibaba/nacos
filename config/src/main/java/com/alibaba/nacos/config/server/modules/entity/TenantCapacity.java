package com.alibaba.nacos.config.server.modules.entity;

import lombok.Data;

import javax.persistence.*;

import static com.alibaba.nacos.config.server.constant.Constants.TENANT_CAPACITY_TABLE_NAME;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@Table(name = TENANT_CAPACITY_TABLE_NAME)
@Entity
@Data
public class TenantCapacity extends Capacity {

    @Column(name = "tenant_id")
    private String tenantId;

}
