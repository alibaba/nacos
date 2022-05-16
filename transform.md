# 1. 背景
Nacos 提供了文件存储和mysql存储两种方式，本文记录了如何增加对 达梦数据库的支持。


# 2. 实现思路

**采用的方式：**
修改nacos源码增加对达梦数据库的支持

我修改后的放到github了，地址：https://github.com/vir56k/add_nacos_support_dameng

下面说下修改过程。

# 3. 操作过程
## 3.1、获得源代码

从 Github 上下载源码方式
```
git clone https://github.com/alibaba/nacos.git
cd nacos/
mvn -Prelease-nacos -Dmaven.test.skip=true clean install -U  
ls -al distribution/target/

// change the $version to your actual path
cd distribution/target/nacos-server-$version/nacos/bin
```

## 3.2、修改源代码
参考这篇文章：
```
https://blog.csdn.net/qq_24101357/article/details/119318033?utm_medium=distribute.pc_aggpage_search_result.none-task-blog-2~aggregatepage~first_rank_v2~rank_aggregation-3-119318033.pc_agg_rank_aggregation&utm_term=nacos%E4%BD%BF%E7%94%A8%E8%BE%BE%E6%A2%A6&spm=1000.2123.3001.4430
```
步骤拆解如下：
**(1)  主工程添加 `达梦8的JDBC驱动类库`的 声明**
修改根路径下的 pom.xml，在 dependencyManagement 节点下添加依赖声明。
```
    <dependencyManagement>
        <dependencies>
            .....
            <dependency>
                <groupId>com.dameng</groupId>
                <artifactId>Dm8JdbcDriver18</artifactId>
                <version>${dm-connector-java.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
```
添加依赖的版本号，注意这里的版本号是 8.1.1.49。是达梦8的驱动。
```
  <properties>
    ...
      <dm-connector-java.version>8.1.1.49</dm-connector-java.version>
    </properties>
```
**(2) 在 nacos-config 模块直接引用驱动库**
修改 nacos-config的pom.xml
```
<dependency>
    <groupId>com.dameng</groupId>
    <artifactId>Dm8JdbcDriver18</artifactId>
</dependency>
```
**(3) 修改nacos-console模块 的配置文件**
nacos-console模块的application.properties：

```
#*************** Config Module Related Configurations ***************#
### If use MySQL as datasource: 这里打开
spring.datasource.platform=mysql

### Count of DB: 这里打开，并新增 dm.jdbc.driver.DmDriver 的驱动
db.num=1
db.jdbcDriverName=dm.jdbc.driver.DmDriver

### Connect URL of DB: 打开并指定 url 连接字符串
#db.url.0=jdbc:mysql://127.0.0.1:3306/nacos?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC
db.url.0=jdbc:dm://192.168.1.22:5236/NACOS?STU&zeroDateTimeBehavior=convertToNull&useUnicode=true&characterEncoding=utf-8
db.user.0=NACOS
db.password.0=xxxxxxxx

```
**(4) 修改源码**
修改 nacos-config 模块下 的 ExternalDataSourceProperties.java 类

添加属性
```
    private String jdbcDriverName;

    public String getJdbcDriverName() {
        return jdbcDriverName;
    }

    public void setJdbcDriverName(String jdbcDriverName) {
        this.jdbcDriverName = jdbcDriverName;
    }

```

为 HikariDataSource 对象 指定驱动名称，调动 setDriverClassName 方法，见下：

```
 /**
     * Build serveral HikariDataSource.
     *
     * @param environment {@link Environment}
     * @param callback    Callback function when constructing data source
     * @return List of {@link HikariDataSource}
     */
    List<HikariDataSource> build(Environment environment, Callback<HikariDataSource> callback) {
        List<HikariDataSource> dataSources = new ArrayList<>();
        Binder.get(environment).bind("db", Bindable.ofInstance(this));
        Preconditions.checkArgument(Objects.nonNull(num), "db.num is null");
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(user), "db.user or db.user.[index] is null");
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(password), "db.password or db.password.[index] is null");
        for (int index = 0; index < num; index++) {
            int currentSize = index + 1;
            Preconditions.checkArgument(url.size() >= currentSize, "db.url.%s is null", index);
            DataSourcePoolProperties poolProperties = DataSourcePoolProperties.build(environment);
            poolProperties.setDriverClassName(JDBC_DRIVER_NAME);
            poolProperties.setJdbcUrl(url.get(index).trim());
            poolProperties.setUsername(getOrDefault(user, index, user.get(0)).trim());
            poolProperties.setPassword(getOrDefault(password, index, password.get(0)).trim());
            HikariDataSource ds = poolProperties.getDataSource();
            ds.setConnectionTestQuery(TEST_QUERY);
            ds.setIdleTimeout(TimeUnit.MINUTES.toMillis(10L));
            ds.setConnectionTimeout(TimeUnit.SECONDS.toMillis(3L));
            System.out.println("#################################");
            System.out.println("jdbcDriverName=" + jdbcDriverName);
            if (StringUtils.isNotEmpty(jdbcDriverName)) {
                // 增加其他数据库驱动的支持
                ds.setDriverClassName(jdbcDriverName);
            } else {
                //默认使用mysql驱动
                ds.setDriverClassName(JDBC_DRIVER_NAME);
            }
            System.out.println("#################################");
            System.out.println("dataSources=" + dataSources);
            dataSources.add(ds);
            callback.accept(ds);
        }
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(dataSources), "no datasource available");
        return dataSources;
    }
```


## 3.3、构建
进入到源代码目录执行：
```
mvn -Prelease-nacos -Dmaven.test.skip=true -Dpmd.skip=true -Dcheckstyle.skip=true clean install -U
```

## 3.4、获得构建完成后的工程
构建后，在 进入到源代码目录 中的 文件夹：
```
distribution/target/nacos-server-$version 下的  nacos 文件夹 就是最终的输出物。
```

## 3.5、最后启动

**启动 nacos**
sh startup.sh -m standalone
**查看启动日志**
tail -f /Users/zhangyunfei/git/1.tongweizhidian/支持达梦改造后的Nacos/nacos/logs/start.out

## 3.6、最后检查
看看数据库中，Nacos 已经使用 达梦数据库来存储了。

# 4.参考：
https://nacos.io/zh-cn/docs/quick-start.html

`https://blog.csdn.net/qq_24101357/article/details/119318033?utm_medium=distribute.pc_aggpage_search_result.none-task-blog-2~aggregatepage~first_rank_v2~rank_aggregation-3-119318033.pc_agg_rank_aggregation&utm_term=nacos%E4%BD%BF%E7%94%A8%E8%BE%BE%E6%A2%A6&spm=1000.2123.3001.4430`

https://blog.csdn.net/denight_alan/article/details/103646314



END