# Nacos Cluster Nginx Load Balancing Guide

This guide explains how to use Nginx as a load balancer for a Nacos cluster,
covering both the HTTP API (port 8848) and gRPC communication (port 9848).

## Background

When connecting to a Nacos cluster, you can configure multiple server addresses
in `server-addr` using commas:

```yaml
spring.cloud.nacos.discovery.server-addr=192.168.190.128:8848,192.168.190.129:8848,192.168.190.130:8848
```

However, this approach has a drawback: when the cluster scales up or down,
you need to repackage and restart your application. Using Nginx as a load
balancer avoids this problem by providing a single entry point.

## Prerequisites

- Nacos cluster with 3+ nodes properly configured
- Nginx installed **with the `stream` module** (`--with-stream` during `./configure`)

> **Important**: The `stream` module is required for gRPC (TCP) proxying. Most
> package-manager installations include it by default, but if you compile Nginx
> from source, you must add `--with-stream`.

## Nacos Cluster Setup

Ensure each Nacos node is configured for cluster mode:

**cluster.conf** (on each node):

```
192.168.190.128:8848
192.168.190.129:8848
192.168.190.130:8848
```

**application.properties** (database configuration):

```properties
spring.datasource.platform=mysql
db.url.0=jdbc:mysql://192.168.190.1:3306/config?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC
db.user.0=root
db.password.0=your_password
```

## Nginx Configuration

Nacos uses two communication protocols:

| Protocol | Default Port | Purpose |
|----------|-------------|---------|
| HTTP     | 8848        | API calls, console access |
| gRPC     | 9848        | Client-server long connections (HTTP port + 1000) |

Your Nginx configuration must handle **both** protocols.

### Complete Nginx Configuration

```nginx
worker_processes 1;

events {
    worker_connections 1024;
}

# HTTP load balancing (for API and console)
http {
    include       mime.types;
    default_type  application/octet-stream;

    client_max_body_size 300m;

    sendfile        on;
    keepalive_timeout 65;

    # Upstream Nacos HTTP servers
    upstream nacos_cluster {
        server 192.168.190.128:8848;
        server 192.168.190.129:8848;
        server 192.168.190.130:8848;
    }

    server {
        listen       7847;
        server_name  localhost;
        charset utf-8;

        location / {
            proxy_pass http://nacos_cluster;
        }
    }
}

# gRPC (TCP) load balancing (for client long connections)
stream {
    # Upstream Nacos gRPC servers
    upstream nacos_grpc_cluster {
        server 192.168.190.128:9848 weight=1;
        server 192.168.190.129:9848 weight=1;
        server 192.168.190.130:9848 weight=1;
    }

    server {
        listen 8847;
        proxy_pass nacos_grpc_cluster;
    }
}
```

### Port Mapping

The Nginx listen ports are chosen to maintain the same offset relationship
as Nacos defaults:

| Nginx Port | Nacos Port | Protocol | Relation |
|-----------|------------|----------|----------|
| 7847      | 8848       | HTTP     | 8848 - 1001 |
| 8847      | 9848       | gRPC     | 9848 - 101 |

> **Note**: Nacos uses a fixed offset of **1000** from the HTTP port for gRPC.
> If your Nacos HTTP port is `8848`, gRPC is on `9848`. The Nginx ports should
> maintain the same offset (7847 + 1000 = 8847 for gRPC).

## Client Configuration

Point your application to the Nginx address instead of individual Nacos nodes:

```yaml
spring.cloud.nacos.discovery.server-addr=192.168.190.128:7847
spring.cloud.nacos.config.server-addr=192.168.190.128:7847
```

For multi-environment setups, you can use Maven profiles in your root `pom.xml`
to switch server addresses per environment.

## Verification

1. Start all Nacos cluster nodes
2. Start Nginx with the above configuration
3. Access the Nacos console at `http://192.168.190.128:7847/nacos`
4. Verify that your application can register services and fetch configurations

## Troubleshooting

### gRPC connection fails

- Ensure Nginx was compiled with `--with-stream` (run `nginx -V` to check)
- Verify the gRPC port (9848) is accessible on all Nacos nodes
- Check that the `stream` block is at the **top level** of `nginx.conf`, not inside `http`

### Client gets stale configurations

- This is a known issue in Nacos cluster mode. See
  [spring-cloud-alibaba#3644](https://github.com/alibaba/spring-cloud-alibaba/pull/3644)
  for the fix that uses config snapshots during refresh.

### Adding new Nacos nodes

When scaling the cluster, update only the Nginx configuration and reload:

```bash
nginx -s reload
```

No application changes or restarts required.
