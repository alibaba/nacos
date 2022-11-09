package com.alibaba.nacos.plugin.control.event;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import org.springframework.stereotype.Component;

@Component
public class DefaultControlDeniedListener {
    
    ConnectionDeniedSubscriber connectionDeniedSubscriber = new ConnectionDeniedSubscriber();
    
    TpsDeniedSubscriber tpsRuleChangeSubscriber = new TpsDeniedSubscriber();
    
    public DefaultControlDeniedListener() {
        NotifyCenter.registerSubscriber(connectionDeniedSubscriber);
        NotifyCenter.registerSubscriber(tpsRuleChangeSubscriber);
    }
    
    class ConnectionDeniedSubscriber extends Subscriber<ConnectionDeniedEvent> {
        
        @Override
        public void onEvent(ConnectionDeniedEvent event) {
            System.out.println("connection denied:" + event.getMessage());
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return ConnectionDeniedEvent.class;
        }
    }
    
    class TpsDeniedSubscriber extends Subscriber<TpsRequestDeniedEvent> {
        
        @Override
        public void onEvent(TpsRequestDeniedEvent event) {
            System.out.println("tps denied:" + event.getMessage());
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return TpsRequestDeniedEvent.class;
        }
    }
}
