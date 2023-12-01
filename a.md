## 
1. ServiceManager 存储所有的服务基本信息
2. IpPortBasedClient 基于ip port作为客户端注册，
3. ConnectionBasedClient 基于连接的客户端 2.x 开始存在
4. AbstractClient 抽象实现 维护客户端的基本信息，（注册的服务和实例信息）

## 暴露端口问题
需要注意 2.x 开始基于长连接需要暴露额外的端口来进行rpc连接，如果需要修改需要同步客户端和服务端
1. 用于客户端操作的端口 NacosPort(8848) + offset(1000);GrpcSdkServer/GrpcSdkClient 
2. 用于集群之间同步信息的连接端口 NacosPort(8848) + offset(1001); GrpcClusterServer/GrpcClusterClient
3. 用于Raft算法选举的端口 NacosPort(8848) - offset(1000); MemberUtil

一共需要的则是四个端口 8848 7848 9848 9849