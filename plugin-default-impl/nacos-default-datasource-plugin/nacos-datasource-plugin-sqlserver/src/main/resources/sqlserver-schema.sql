-- =============================================
-- 表: config_info
-- =============================================
CREATE TABLE [config_info] (
    [id]                  BIGINT           NOT NULL IDENTITY(1,1) PRIMARY KEY, -- 'id'
    [data_id]             NVARCHAR(255)    NOT NULL,                            -- 'data_id'
    [group_id]            NVARCHAR(128)    NULL,                                -- 'group_id'
    [content]             NVARCHAR(MAX)    NOT NULL,                            -- 'content'
    [md5]                 NVARCHAR(32)     NULL,                                -- 'md5'
    [gmt_create]          DATETIME         NOT NULL DEFAULT GETDATE(),          -- '创建时间'
    [gmt_modified]        DATETIME         NOT NULL DEFAULT GETDATE(),          -- '修改时间'
    [src_user]            NVARCHAR(MAX)    NULL,                                -- 'source user'
    [src_ip]              NVARCHAR(50)     NULL,                                -- 'source ip'
    [app_name]            NVARCHAR(128)    NULL,                                -- 'app_name'
    [tenant_id]           NVARCHAR(128)    NOT NULL DEFAULT '',                 -- '租户字段'
    [c_desc]              NVARCHAR(256)    NULL,                                -- 'configuration description'
    [c_use]               NVARCHAR(64)     NULL,                                -- 'configuration usage'
    [effect]              NVARCHAR(64)     NULL,                                -- '配置生效的描述'
    [type]                NVARCHAR(64)     NULL,                                -- '配置的类型'
    [c_schema]            NVARCHAR(MAX)    NULL,                                -- '配置的模式'
    [encrypted_data_key]  NVARCHAR(1024)   NOT NULL DEFAULT ''                  -- '密钥'
);

-- 唯一约束
ALTER TABLE [config_info] ADD CONSTRAINT uk_configinfo_datagrouptenant
    UNIQUE ([data_id], [group_id], [tenant_id]);

-- =============================================
-- 表: config_info_gray
-- =============================================
CREATE TABLE [config_info_gray] (
    [id]                  BIGINT            NOT NULL IDENTITY(1,1) PRIMARY KEY, -- 'id'
    [data_id]             NVARCHAR(255)     NOT NULL,                            -- 'data_id'
    [group_id]            NVARCHAR(128)     NOT NULL,                            -- 'group_id'
    [content]             NVARCHAR(MAX)     NOT NULL,                            -- 'content'
    [md5]                 NVARCHAR(32)      NULL,                                -- 'md5'
    [src_user]            NVARCHAR(MAX)     NULL,                                -- 'src_user'
    [src_ip]              NVARCHAR(100)     NULL,                                -- 'src_ip'
    [gmt_create]          DATETIME2(3)      NOT NULL DEFAULT SYSDATETIME(),      -- 'gmt_create'
    [gmt_modified]        DATETIME2(3)      NOT NULL DEFAULT SYSDATETIME(),      -- 'gmt_modified'
    [app_name]            NVARCHAR(128)     NULL,                                -- 'app_name'
    [tenant_id]           NVARCHAR(128)     NOT NULL DEFAULT '',                 -- 'tenant_id'
    [gray_name]           NVARCHAR(128)     NOT NULL,                            -- 'gray_name'
    [gray_rule]           NVARCHAR(MAX)     NOT NULL,                            -- 'gray_rule'
    [encrypted_data_key]  NVARCHAR(256)     NOT NULL DEFAULT ''                  -- 'encrypted_data_key'
);

-- 唯一约束
ALTER TABLE [config_info_gray] ADD CONSTRAINT uk_configinfogray_datagrouptenantgray
    UNIQUE ([data_id], [group_id], [tenant_id], [gray_name]);

-- 索引
CREATE INDEX idx_dataid_gmt_modified ON [config_info_gray] ([data_id], [gmt_modified]);
CREATE INDEX idx_gmt_modified ON [config_info_gray] ([gmt_modified]);

-- =============================================
-- 表: config_tags_relation
-- =============================================
CREATE TABLE [config_tags_relation] (
    [id]          BIGINT          NOT NULL,                           -- 'id'
    [tag_name]    NVARCHAR(128)   NOT NULL,                           -- 'tag_name'
    [tag_type]    NVARCHAR(64)    NULL,                               -- 'tag_type'
    [data_id]     NVARCHAR(255)   NOT NULL,                           -- 'data_id'
    [group_id]    NVARCHAR(128)   NOT NULL,                           -- 'group_id'
    [tenant_id]   NVARCHAR(128)   NOT NULL DEFAULT '',                -- 'tenant_id'
    [nid]         BIGINT          NOT NULL IDENTITY(1,1) PRIMARY KEY  -- 'nid, 自增长标识'
);

-- 唯一约束
ALTER TABLE [config_tags_relation] ADD CONSTRAINT uk_configtagrelation_configidtag
    UNIQUE ([id], [tag_name], [tag_type]);

-- 索引
CREATE INDEX idx_tenant_id ON [config_tags_relation] ([tenant_id]);

-- =============================================
-- 表: group_capacity
-- =============================================
CREATE TABLE [group_capacity] (
    [id]                 BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,  -- '主键ID'
    [group_id]           NVARCHAR(128) NOT NULL DEFAULT '',                 -- 'Group ID，空字符表示整个集群'
    [quota]              INT           NOT NULL DEFAULT 0,                  -- '配额，0表示使用默认值'
    [usage]              INT           NOT NULL DEFAULT 0,                  -- '使用量'
    [max_size]           INT           NOT NULL DEFAULT 0,                  -- '单个配置大小上限，单位为字节，0表示使用默认值'
    [max_aggr_count]     INT           NOT NULL DEFAULT 0,                  -- '聚合子配置最大个数，，0表示使用默认值'
    [max_aggr_size]      INT           NOT NULL DEFAULT 0,                  -- '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值'
    [max_history_count]  INT           NOT NULL DEFAULT 0,                  -- '最大变更历史数量'
    [gmt_create]         DATETIME      NOT NULL DEFAULT GETDATE(),          -- '创建时间'
    [gmt_modified]       DATETIME      NOT NULL DEFAULT GETDATE()           -- '修改时间'
);

-- 唯一约束
ALTER TABLE [group_capacity] ADD CONSTRAINT uk_group_id UNIQUE ([group_id]);

-- =============================================
-- 表: his_config_info
-- =============================================
CREATE TABLE [his_config_info] (
    [id]                  BIGINT          NOT NULL,                              -- 'id'
    [nid]                 BIGINT          NOT NULL IDENTITY(1,1) PRIMARY KEY,    -- 'nid, 自增标识'
    [data_id]             NVARCHAR(255)   NOT NULL,                              -- 'data_id'
    [group_id]            NVARCHAR(128)   NOT NULL,                              -- 'group_id'
    [app_name]            NVARCHAR(128)   NULL,                                  -- 'app_name'
    [content]             NVARCHAR(MAX)   NOT NULL,                              -- 'content'
    [md5]                 NVARCHAR(32)    NULL,                                  -- 'md5'
    [gmt_create]          DATETIME        NOT NULL DEFAULT GETDATE(),            -- '创建时间'
    [gmt_modified]        DATETIME        NOT NULL DEFAULT GETDATE(),            -- '修改时间'
    [src_user]            NVARCHAR(MAX)   NULL,                                  -- 'source user'
    [src_ip]              NVARCHAR(50)    NULL,                                  -- 'source ip'
    [op_type]             NCHAR(10)       NULL,                                  -- 'operation type'
    [tenant_id]           NVARCHAR(128)   NOT NULL DEFAULT '',                   -- '租户字段'
    [encrypted_data_key]  NVARCHAR(1024)  NOT NULL DEFAULT '',                   -- '密钥'
    [publish_type]        NVARCHAR(50)    NOT NULL DEFAULT 'formal',             -- 'publish type gray or formal'
    [gray_name]           NVARCHAR(50)    NULL,                                  -- 'gray name'
    [ext_info]            NVARCHAR(MAX)   NULL                                   -- 'ext info'
);

-- 索引
CREATE INDEX idx_gmt_create ON [his_config_info] ([gmt_create]);
CREATE INDEX idx_gmt_modified ON [his_config_info] ([gmt_modified]);
CREATE INDEX idx_did ON [his_config_info] ([data_id]);

-- =============================================
-- 表: tenant_capacity
-- =============================================
CREATE TABLE [tenant_capacity] (
    [id]                 BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,  -- '主键ID'
    [tenant_id]          NVARCHAR(128) NOT NULL DEFAULT '',                 -- 'Tenant ID'
    [quota]              INT           NOT NULL DEFAULT 0,                  -- '配额，0表示使用默认值'
    [usage]              INT           NOT NULL DEFAULT 0,                  -- '使用量'
    [max_size]           INT           NOT NULL DEFAULT 0,                  -- '单个配置大小上限，单位为字节，0表示使用默认值'
    [max_aggr_count]     INT           NOT NULL DEFAULT 0,                  -- '聚合子配置最大个数'
    [max_aggr_size]      INT           NOT NULL DEFAULT 0,                  -- '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值'
    [max_history_count]  INT           NOT NULL DEFAULT 0,                  -- '最大变更历史数量'
    [gmt_create]         DATETIME      NOT NULL DEFAULT GETDATE(),          -- '创建时间'
    [gmt_modified]       DATETIME      NOT NULL DEFAULT GETDATE()           -- '修改时间'
);

-- 唯一约束
ALTER TABLE [tenant_capacity] ADD CONSTRAINT uk_tenant_id UNIQUE ([tenant_id]);

-- =============================================
-- 表: tenant_info
-- =============================================
CREATE TABLE [tenant_info] (
    [id]              BIGINT          NOT NULL IDENTITY(1,1) PRIMARY KEY, -- 'id'
    [kp]              NVARCHAR(128)   NOT NULL,                           -- 'kp'
    [tenant_id]       NVARCHAR(128)   NOT NULL DEFAULT '',                -- 'tenant_id'
    [tenant_name]     NVARCHAR(128)   NOT NULL DEFAULT '',                -- 'tenant_name'
    [tenant_desc]     NVARCHAR(256)   NULL,                               -- 'tenant_desc'
    [create_source]   NVARCHAR(32)    NULL,                               -- 'create_source'
    [gmt_create]      BIGINT          NOT NULL,                           -- '创建时间'
    [gmt_modified]    BIGINT          NOT NULL                            -- '修改时间'
);

-- 唯一约束
ALTER TABLE [tenant_info] ADD CONSTRAINT uk_tenant_info_kptenantid UNIQUE ([kp], [tenant_id]);

-- 索引
CREATE INDEX idx_tenant_id ON [tenant_info] ([tenant_id]);

-- =============================================
-- 表: users
-- =============================================
CREATE TABLE [users] (
    [username] NVARCHAR(50)  NOT NULL PRIMARY KEY, -- 'username'
    [password] NVARCHAR(500) NOT NULL,             -- 'password'
    [enabled]  BIT           NOT NULL              -- 'enabled'
);

-- =============================================
-- 表: roles
-- =============================================
CREATE TABLE [roles] (
    [username] NVARCHAR(50) NOT NULL, -- 'username'
    [role]     NVARCHAR(50) NOT NULL  -- 'role'
);

-- 唯一索引
CREATE UNIQUE INDEX idx_user_role ON [roles] ([username] ASC, [role] ASC);

-- =============================================
-- 表: permissions
-- =============================================
CREATE TABLE [permissions] (
    [role]     NVARCHAR(50)  NOT NULL, -- 'role'
    [resource] NVARCHAR(128) NOT NULL, -- 'resource'
    [action]   NVARCHAR(8)   NOT NULL  -- 'action'
);

-- 唯一索引
CREATE UNIQUE INDEX uk_role_permission ON [permissions] ([role], [resource], [action]);

-- =============================================
-- 表: pipeline_execution
-- =============================================
CREATE TABLE [pipeline_execution] (
    [execution_id]   NVARCHAR(64)  NOT NULL PRIMARY KEY, -- '执行ID'
    [resource_type]  NVARCHAR(32)  NOT NULL,             -- '资源类型'
    [resource_name]  NVARCHAR(256) NOT NULL,             -- '资源名称'
    [namespace_id]   NVARCHAR(128) NULL,                 -- '命名空间ID'
    [version]        NVARCHAR(64)  NULL,                 -- '版本'
    [status]         NVARCHAR(32)  NOT NULL,             -- '执行状态'
    [pipeline]       NVARCHAR(MAX) NOT NULL,             -- 'pipeline节点结果JSON'
    [create_time]    BIGINT        NOT NULL,             -- '创建时间'
    [update_time]    BIGINT        NOT NULL              -- '修改时间'
);

-- =============================================
-- 表: ai_resource
-- =============================================
CREATE TABLE [ai_resource] (
    [id]              BIGINT          NOT NULL IDENTITY(1,1) PRIMARY KEY,  -- 'id'
    [gmt_create]      DATETIME        NOT NULL DEFAULT GETDATE(),          -- '创建时间'
    [gmt_modified]    DATETIME        NOT NULL DEFAULT GETDATE(),          -- '修改时间'
    [name]            NVARCHAR(256)   NOT NULL,                            -- '资源名称'
    [type]            NVARCHAR(32)    NOT NULL,                            -- '资源类型'
    [c_desc]          NVARCHAR(2048)  NULL,                                -- '资源描述'
    [status]          NVARCHAR(32)    NULL,                                -- '资源状态'
    [namespace_id]    NVARCHAR(128)   NOT NULL DEFAULT '',                 -- '命名空间ID'
    [biz_tags]        NVARCHAR(1024)  NULL,                                -- '业务标签'
    [ext]             NVARCHAR(MAX)   NULL,                                -- '扩展信息(JSON)'
    [c_from]          NVARCHAR(256)   NOT NULL DEFAULT 'local',            -- '来源标识(导入/同步来源)'
    [version_info]    NVARCHAR(MAX)   NULL,                                -- '版本信息(JSON)'
    [meta_version]    BIGINT          NOT NULL DEFAULT 1,                  -- '元数据版本(乐观锁)'
    [scope]           NVARCHAR(16)    NOT NULL DEFAULT 'PRIVATE',          -- '可见性: PUBLIC/PRIVATE'
    [owner]           NVARCHAR(128)   NOT NULL DEFAULT '',                 -- '创建者用户名'
    [download_count]  BIGINT          NOT NULL DEFAULT 0                   -- '下载次数'
);

-- 唯一约束
ALTER TABLE [ai_resource] ADD CONSTRAINT uk_ai_resource_ns_name_type
    UNIQUE ([namespace_id], [name], [type], [c_from]);

-- 索引
CREATE INDEX idx_ai_resource_name ON [ai_resource] ([name]);
CREATE INDEX idx_ai_resource_type ON [ai_resource] ([type]);
CREATE INDEX idx_ai_resource_gmt_modified ON [ai_resource] ([gmt_modified]);

-- =============================================
-- 表: ai_resource_version
-- =============================================
CREATE TABLE [ai_resource_version] (
    [id]                     BIGINT          NOT NULL IDENTITY(1,1) PRIMARY KEY,  -- 'id'
    [gmt_create]             DATETIME        NOT NULL DEFAULT GETDATE(),          -- '创建时间'
    [gmt_modified]           DATETIME        NOT NULL DEFAULT GETDATE(),          -- '修改时间'
    [type]                   NVARCHAR(32)    NOT NULL,                            -- '资源类型'
    [author]                 NVARCHAR(128)   NULL,                                -- '作者'
    [name]                   NVARCHAR(256)   NOT NULL,                            -- '资源名称'
    [c_desc]                 NVARCHAR(2048)  NULL,                                -- '版本描述'
    [status]                 NVARCHAR(32)    NOT NULL,                            -- '版本状态'
    [version]                NVARCHAR(64)    NOT NULL,                            -- '版本号'
    [namespace_id]           NVARCHAR(128)   NOT NULL DEFAULT '',                 -- '命名空间ID'
    [storage]                NVARCHAR(MAX)   NULL,                                -- '存储信息(JSON)'
    [publish_pipeline_info]  NVARCHAR(MAX)   NULL,                                -- '发布流水线信息(JSON)'
    [download_count]         BIGINT          NOT NULL DEFAULT 0                   -- '下载次数'
);

-- 唯一约束
ALTER TABLE [ai_resource_version] ADD CONSTRAINT uk_ai_resource_ver_ns_name_type_ver
    UNIQUE ([namespace_id], [name], [type], [version]);

-- 索引
CREATE INDEX idx_ai_resource_ver_name ON [ai_resource_version] ([name]);
CREATE INDEX idx_ai_resource_ver_status ON [ai_resource_version] ([status]);
CREATE INDEX idx_ai_resource_ver_gmt_modified ON [ai_resource_version] ([gmt_modified]);
