package com.alibaba.nacos.config.server.modules.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

import static com.alibaba.nacos.config.server.constant.Constants.CONFIG_INFO_TAG_TABLE_NAME;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@Table(name = CONFIG_INFO_TAG_TABLE_NAME)
@Entity
@Data
public class ConfigInfoTag implements Serializable {


    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_id")
    private String dataId;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "tag_id")
    private String tagId;

    @Column(name = "app_name")
    private String appName;

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


}
