package com.alibaba.nacos.config.server.modules.repository;

import com.alibaba.nacos.config.server.modules.entity.ConfigInfoAggr;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
public interface ConfigInfoAggrRepository extends PagingAndSortingRepository<ConfigInfoAggr, Long>,
    QuerydslPredicateExecutor<ConfigInfoAggr> {

    @Query(value = "SELECT DISTINCT data_id,group_id,tenant_id,id,app_name,content,gmt_modified,datum_id FROM config_info_aggr", nativeQuery = true)
    List<ConfigInfoAggr> findAllAggrGroup();

}
