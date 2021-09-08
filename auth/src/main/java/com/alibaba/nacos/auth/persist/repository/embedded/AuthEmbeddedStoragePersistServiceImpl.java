package com.alibaba.nacos.auth.persist.repository.embedded;

import com.alibaba.nacos.auth.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.auth.persist.datasource.DataSourceService;
import com.alibaba.nacos.auth.persist.datasource.AuthDynamicDataSource;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
public class AuthEmbeddedStoragePersistServiceImpl {
    
    private DataSourceService dataSourceService;
    
    private final DatabaseOperate databaseOperate;
    
    public AuthEmbeddedStoragePersistServiceImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
    }
    
    /**
     * init datasource.
     */
    @PostConstruct
    public void init() {
        dataSourceService = AuthDynamicDataSource.getInstance().getDataSource();
    }
    
    public <E> EmbeddedPaginationHelperImpl<E> createPaginationHelper() {
        return new EmbeddedPaginationHelperImpl<E>(databaseOperate);
    }
    
    /**
     * For unit testing.
     */
    public JdbcTemplate getJdbcTemplate() {
        return this.dataSourceService.getJdbcTemplate();
    }
    
    public DatabaseOperate getDatabaseOperate() {
        return databaseOperate;
    }
    
}
