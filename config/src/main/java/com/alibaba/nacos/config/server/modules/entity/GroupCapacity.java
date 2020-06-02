package com.alibaba.nacos.config.server.modules.entity;

import lombok.Data;

import javax.persistence.*;

import static com.alibaba.nacos.config.server.constant.Constants.GROUP_CAPACITY_TABLE_NAME;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@Table(name = GROUP_CAPACITY_TABLE_NAME)
@Entity
@Data
public class GroupCapacity extends Capacity {

    @Column(name = "group_id")
    private String groupId;

}
