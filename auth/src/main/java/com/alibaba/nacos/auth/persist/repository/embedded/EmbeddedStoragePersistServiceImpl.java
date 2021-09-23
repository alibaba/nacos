package com.alibaba.nacos.auth.persist.repository.embedded;

import com.alibaba.nacos.auth.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.auth.persist.datasource.DataSourceService;
import com.alibaba.nacos.auth.persist.datasource.DynamicDataSource;
import com.alibaba.nacos.auth.persist.repository.PaginationHelper;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Conditional(value = ConditionOnEmbeddedStorage.class)
@Component
public class EmbeddedStoragePersistServiceImpl {
    
    private DataSourceService dataSourceService;
    
    private final DatabaseOperate databaseOperate;
    
    public EmbeddedStoragePersistServiceImpl(DatabaseOperate databaseOperate) {
        this.databaseOperate = databaseOperate;
    }
    
    /**
     * init datasource.
     */
    @PostConstruct
    public void init() {
        dataSourceService = DynamicDataSource.getInstance().getDataSource();
    }
    
    public <E> PaginationHelper<E> createPaginationHelper() {
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
