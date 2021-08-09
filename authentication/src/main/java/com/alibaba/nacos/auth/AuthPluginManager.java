package authentication.src.main.java.com.alibaba.nacos.auth;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import java.util.Collection;
import java.util.Iterator;

public class AuthPluginManager {

    public void initAuthPlugins(){
        Collection<AuthService> authServices = NacosServiceLoader.load(AuthService.class);
        
        Iterator<AuthService> authIterator = authServices.iterator();
        boolean pluginNotFound = true;
        if (authIterator.hasNext()) {
            pluginNotFound = false;
        }
        if (pluginNotFound) {
            System.out.println("AuthService load fail!");
        } else {
            for (AuthService authsercice : authServices) {
                System.out.println(authsercice.getClass());
            }
        }
        
    }
}
