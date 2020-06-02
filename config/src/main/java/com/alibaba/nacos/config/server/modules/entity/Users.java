package com.alibaba.nacos.config.server.modules.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

import static com.alibaba.nacos.config.server.constant.Constants.USERS_TABLE_NAME;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@NoArgsConstructor
@AllArgsConstructor
@Table(name = USERS_TABLE_NAME)
@Entity
@Data
public class Users implements Serializable {

    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "enabled")
    private Integer enabled;

}
