package com.alibaba.nacos.config.server.modules.entity;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

import static com.alibaba.nacos.config.server.constant.Constants.CONFIG_INFO_TABLE_NAME;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */

@Data
@Entity
@Table(name = CONFIG_INFO_TABLE_NAME)
public class ConfigInfo implements Serializable {

    //jpa
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "data_id")
    private String dataId;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "content")
    private String content;

    @Column(name = "md5")
    private String md5;

    @Column(name = "gmt_create")
    private Date gmtCreate;

    @Column(name = "gmt_modified")
    private Date gmtModified;

    @Column(name = "src_user")
    private String srcUser;

    @Column(name = "src_ip")
    private String srcIp;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "c_desc")
    private String cDesc;

    @Column(name = "c_use")
    private String cUse;

    @Column(name = "effect")
    private String effect;

    @Column(name = "type")
    private String type;

    @Column(name = "c_schema")
    private String cSchema;

    public ConfigInfo() {
    }

    public ConfigInfo(String dataId, String groupId, String content, String appName, String tenantId) {
        this.dataId = dataId;
        this.groupId = groupId;
        this.content = content;
        this.appName = appName;
        this.tenantId = tenantId;
    }
}
