package com.alibaba.nacos.config.server.modules.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

import static com.alibaba.nacos.config.server.constant.Constants.TENANT_INFO_TABLE_NAME;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@Table(name = TENANT_INFO_TABLE_NAME)
@Entity
@Data
public class TenantInfo implements Serializable {


    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kp")
    private String kp;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "tenant_name")
    private String tenantName;

    @Column(name = "tenant_desc")
    private String tenantDesc;

    @Column(name = "create_source")
    private String createSource;

    @Column(name = "gmt_create")
    private Long gmtCreate;

    @Column(name = "gmt_modified")
    private Long gmtModified;

}
