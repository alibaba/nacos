package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.api.naming.spi.generator.IdGenerator;
import com.alibaba.nacos.core.distributed.id.SnowFlowerIdGenerator;

/**
 * Created by chenwenshun on 2023/2/16.
 */
public class SnowFlakeInstanceIdGenerator implements IdGenerator {

    public static final String ID_DELIMITER = "#";

    private final String serviceName;

    private final String clusterName;

    private final int port;

    private static final SnowFlowerIdGenerator SNOW_FLOWER_ID_GENERATOR = new SnowFlowerIdGenerator();

    public SnowFlakeInstanceIdGenerator(String serviceName, String clusterName, int port) {
        this.serviceName = serviceName;
        this.clusterName = clusterName;
        this.port = port;
    }

    @Override
    public String generateInstanceId() {
        return SNOW_FLOWER_ID_GENERATOR.nextId() + ID_DELIMITER + port + ID_DELIMITER + clusterName + ID_DELIMITER + serviceName;
    }
}
