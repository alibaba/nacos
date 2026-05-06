/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.namespace.repository;

import com.alibaba.nacos.core.namespace.model.TenantInfo;
import com.alibaba.nacos.persistence.repository.RowMapperManager;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Namespace row mapper injector.
 *
 * @author xiweng.yy
 */
@Component
public class NamespaceRowMapperInjector {
    
    public static final RowMapper<TenantInfo> TENANT_INFO_ROW_MAPPER = new TenantInfoRowMapper();
    
    public NamespaceRowMapperInjector() {
        injectNamespaceRowMapper();
    }
    
    private void injectNamespaceRowMapper() {
        // TENANT_INFO_ROW_MAPPER
        RowMapperManager
                .registerRowMapper(NamespaceRowMapperInjector.TENANT_INFO_ROW_MAPPER.getClass().getCanonicalName(),
                        NamespaceRowMapperInjector.TENANT_INFO_ROW_MAPPER);
    }
    
    public static final class TenantInfoRowMapper implements RowMapper<TenantInfo> {
        
        @Override
        public TenantInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            TenantInfo info = new TenantInfo();
            info.setTenantId(rs.getString("tenant_id"));
            info.setTenantName(rs.getString("tenant_name"));
            info.setTenantDesc(rs.getString("tenant_desc"));
            return info;
        }
    }
}
