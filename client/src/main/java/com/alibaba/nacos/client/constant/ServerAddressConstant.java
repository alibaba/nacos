package com.alibaba.nacos.client.constant;

/**
 * @author HuHan
 * @date 2022/8/1 18:22
 */
public class ServerAddressConstant {

    private static String serverAddress;

    public static String getServerAddress() {
        return serverAddress;
    }

    public static void setServerAddress(String serverAddress) {
        ServerAddressConstant.serverAddress = serverAddress;
    }
}
