package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.nacos.DefaultNacosTpsBarrierCreator;

import java.util.Collection;

public class TpsBarrierCreatorProxy {
    
    static TpsBarrierCreator tpsBarrierCreator;
    
    static {
        String tpsRuleBarrierCreator = ControlConfigs.getInstance().getTpsRuleBarrierCreator();
        Collection<TpsBarrierCreator> loadedCreators = NacosServiceLoader.load(TpsBarrierCreator.class);
        for (TpsBarrierCreator barrierCreator : loadedCreators) {
            if (tpsRuleBarrierCreator.equalsIgnoreCase(barrierCreator.getName())) {
                Loggers.CONTROL.info("Found tps barrier creator of name : {}", tpsBarrierCreator);
                tpsBarrierCreator = barrierCreator;
                break;
            }
        }
        if (tpsBarrierCreator == null) {
            Loggers.CONTROL.warn("Fail to found tps barrier creator of name : {},use  default local simple creator",
                    tpsRuleBarrierCreator);
            tpsBarrierCreator = new DefaultNacosTpsBarrierCreator();
        }
    }
    
    public static TpsBarrierCreator getTpsBarrierCreator() {
        return tpsBarrierCreator;
    }
}
