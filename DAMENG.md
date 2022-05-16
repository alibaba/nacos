## Chrisme added DaMeng support at 20220516154451

### add below to applicaton.properties

```properties

### If use MySQL as datasource: 这里打开
spring.datasource.platform=mysql

### Count of DB: 这里打开，并新增 dm.jdbc.driver.DmDriver 的驱动
db.num=1
db.jdbcDriverName=dm.jdbc.driver.DmDriver

### Connect URL of DB: 打开并指定 url 连接字符串
db.url.0=jdbc:dm://10.15.34.115:5236/NACOS?STU&zeroDateTimeBehavior=convertToNull&useUnicode=true&characterEncoding=utf-8
db.user.0=NACOS
db.password.0=Bxsoft12#$%

```
If you has any suggestions, please contact me by send email to chr@bxsoft.cn