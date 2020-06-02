package com.alibaba.nacos.config.server.modules.repository;

import com.alibaba.nacos.config.server.modules.entity.ConfigInfo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * @author paderlol
 * @date: 2020/1/18 15:49
 */
public interface ConfigInfoRepository extends PagingAndSortingRepository<ConfigInfo, Long>,
    QuerydslPredicateExecutor<ConfigInfo> {

    List<ConfigInfo> findByDataIdAndGroupIdAndTenantId(String dataId,String groupId,String tenantId);



}
