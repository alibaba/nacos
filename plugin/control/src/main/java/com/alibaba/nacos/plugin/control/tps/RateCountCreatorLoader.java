package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.ArrayList;
import java.util.List;

public class RateCountCreatorLoader {
    
    List<RateCountCreator> loaders;
    
    private RateCountCreatorLoader() {
        loaders = new ArrayList<RateCountCreator>(NacosServiceLoader.load(RateCountCreator.class));
        
    }
    
    RateCountCreator getRateCountCreator() {
        return loaders.get(0);
    }
    
    public static final RateCountCreatorLoader getInstance() {
        return new RateCountCreatorLoader();
    }
}
