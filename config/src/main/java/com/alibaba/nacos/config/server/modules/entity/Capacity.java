package com.alibaba.nacos.config.server.modules.entity;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

@Data
@MappedSuperclass
public abstract class Capacity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quota")
    private Integer quota;

    //usage 是mysql中关键字所以需要转义
    @Column(name = "`usage`")
    private Integer usage;

    @Column(name = "max_size")
    private Integer maxSize;

    @Column(name = "max_aggr_count")
    private Integer maxAggrCount;

    @Column(name = "max_aggr_size")
    private Integer maxAggrSize;

    @Column(name = "max_history_count")
    private Integer maxHistoryCount;

    @Column(name = "gmt_create")
    private Date gmtCreate;

    @Column(name = "gmt_modified")
    private Date gmtModified;
}
