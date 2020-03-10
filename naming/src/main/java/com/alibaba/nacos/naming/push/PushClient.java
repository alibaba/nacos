package com.alibaba.nacos.naming.push;

import com.alibaba.nacos.api.naming.NamingSuscribeType;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;

public abstract class PushClient {

    protected String type = NamingSuscribeType.NONE.name();

    protected String namespaceId;
    protected String serviceName;
    protected String clusters;
    protected String source;
    protected String agent;
    protected long lastRefTime = System.currentTimeMillis();
    protected DataSource dataSource;

    protected Map<String, String> metadata;

    public String getClusters() {
        return clusters;
    }

    public void setClusters(String clusters) {
        this.clusters = clusters;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getType() {
        return type;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public long getLastRefTime() {
        return lastRefTime;
    }

    public void setLastRefTime(long lastRefTime) {
        this.lastRefTime = lastRefTime;
    }

    public void refresh() {
        lastRefTime = System.currentTimeMillis();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public abstract String getKey();

    public static class UdpPushClient extends PushClient {

        private String agent;
        private String tenant;
        private String app;
        private InetSocketAddress socketAddr;
        private Map<String, String[]> params;

        public Map<String, String[]> getParams() {
            return params;
        }

        public void setParams(Map<String, String[]> params) {
            this.params = params;
        }

        public UdpPushClient(String namespaceId,
                             String serviceName,
                             String clusters,
                             String agent,
                             InetSocketAddress socketAddr,
                             DataSource dataSource,
                             String tenant,
                             String app) {
            this.namespaceId = namespaceId;
            this.serviceName = serviceName;
            this.clusters = clusters;
            this.agent = agent;
            this.socketAddr = socketAddr;
            this.dataSource = dataSource;
            this.tenant = tenant;
            this.app = app;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("serviceName: ").append(serviceName)
                .append(", clusters: ").append(clusters)
                .append(", address: ").append(socketAddr)
                .append(", agent: ").append(agent);
            return sb.toString();
        }

        public String getAddrStr() {
            return socketAddr.getAddress().getHostAddress() + ":" + socketAddr.getPort();
        }

        public String getIp() {
            return socketAddr.getAddress().getHostAddress();
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceName, clusters, socketAddr);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof UdpPushClient)) {
                return false;
            }

            UdpPushClient other = (UdpPushClient) obj;

            return serviceName.equals(other.serviceName) && clusters.equals(other.clusters) && socketAddr.equals(other.socketAddr);
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }

        public String getApp() {
            return app;
        }

        public void setApp(String app) {
            this.app = app;
        }

        public InetSocketAddress getSocketAddr() {
            return socketAddr;
        }

        @Override
        public String getKey() {
            return getAddrStr();
        }
    }

    public static class GrpcPushClient extends PushClient {

        private String clientId;

        public GrpcPushClient(String namespaceId,
                              String serviceName,
                              String clientId,
                              String clusters,
                              DataSource dataSource) {
            this.type = NamingSuscribeType.GRPC.name();
            this.namespaceId = namespaceId;
            this.serviceName = serviceName;
            this.clientId = clientId;
            this.clusters = clusters;
            this.dataSource = dataSource;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        @Override
        public String getKey() {
            return clientId;
        }
    }
}
