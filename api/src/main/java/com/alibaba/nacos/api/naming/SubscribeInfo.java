package com.alibaba.nacos.api.naming;

public class SubscribeInfo {

    protected String type = NamingSuscribeType.NONE.name();

    public String getType() {
        return type;
    }

    public static SubscribeInfo noneSubscribe() {
        return new SubscribeInfo();
    }

    public static SubscribeInfo udpSubscribe(int port) {
        return new UdpSubscribeInfo(port);
    }

    public static SubscribeInfo grpcSubscribe() {
        return new GrpcSubscribeInfo();
    }

    public static class UdpSubscribeInfo extends SubscribeInfo {
        private int udpPort;

        public UdpSubscribeInfo(int udpPort) {
            this.type = NamingSuscribeType.UDP.name();
            this.udpPort = udpPort;
        }

        public int getUdpPort() {
            return udpPort;
        }
    }

    public static class GrpcSubscribeInfo extends SubscribeInfo {
        public GrpcSubscribeInfo() {
            this.type = NamingSuscribeType.GRPC.name();
        }
    }
}
