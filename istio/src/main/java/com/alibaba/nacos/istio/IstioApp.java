package com.alibaba.nacos.istio;

import com.alibaba.nacos.istio.mcp.NacosMcpServer;

/**
 * @author nkorange
 * @since 1.1.4
 */
public class IstioApp {

    public static void main(String[] args) throws Exception {

        final NacosMcpServer server = new NacosMcpServer();

        server.start();

        server.waitForTerminated();
    }
}
