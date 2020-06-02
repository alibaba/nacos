package com.alibaba.nacos.config.server.modules.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

import static com.alibaba.nacos.config.server.constant.Constants.ROLES_TABLE_NAME;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@AllArgsConstructor
@NoArgsConstructor
@Table(name = ROLES_TABLE_NAME)
@Entity
@Data
public class Roles implements Serializable {


    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "role")
    private String role;

}
