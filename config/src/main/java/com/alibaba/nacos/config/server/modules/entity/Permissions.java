package com.alibaba.nacos.config.server.modules.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

import static com.alibaba.nacos.config.server.constant.Constants.PERMISSIONS_TABLE_NAME;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@NoArgsConstructor
@AllArgsConstructor
@Table(name = PERMISSIONS_TABLE_NAME)
@Entity
@Data
public class Permissions implements Serializable {


    @Id
    @Column(name = "role")
    private String role;

    @Column(name = "resource")
    private String resource;

    @Column(name = "action")
    private String action;

}
