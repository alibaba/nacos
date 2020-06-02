package com.alibaba.nacos.config.server.modules.repository;

import com.alibaba.nacos.config.server.modules.entity.ConfigInfoBeta;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
public interface ConfigInfoBetaRepository extends PagingAndSortingRepository<ConfigInfoBeta, Long>,
    QuerydslPredicateExecutor<ConfigInfoBeta> {

}
