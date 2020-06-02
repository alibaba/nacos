package com.alibaba.nacos.config.server.modules.repository;

import com.alibaba.nacos.config.server.modules.entity.HisConfigInfo;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * @author paderlol
 * @date: 2020/1/18 15:49
 */
public interface HisConfigInfoRepository extends PagingAndSortingRepository<HisConfigInfo, Long>,
    QuerydslPredicateExecutor<HisConfigInfo> {

}
