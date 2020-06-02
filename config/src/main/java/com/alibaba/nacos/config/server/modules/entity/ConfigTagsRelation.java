package com.alibaba.nacos.config.server.modules.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

import static com.alibaba.nacos.config.server.constant.Constants.CONFIG_TAGS_RELATION_TABLE_NAME;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@Table(name = CONFIG_TAGS_RELATION_TABLE_NAME)
@Entity
@Data
public class ConfigTagsRelation implements Serializable {


    @Id
    @Column(name = "nid")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long nid;

    @Column(name = "id")
    private Long id;

    @Column(name = "tag_name")
    private String tagName;

    @Column(name = "tag_type")
    private String tagType;

    @Column(name = "data_id")
    private String dataId;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "tenant_id")
    private String tenantId;



}
