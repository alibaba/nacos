package authentication.src.test.java.com.alibaba.nacos.auth;

import authentication.src.main.java.com.alibaba.nacos.auth.AuthPluginManager;
import com.alibaba.nacos.api.naming.selector.Selector;

public class AuthPluginTest {
    
    public void testAuthPlugin() {
        
    }
    public static void main(String[] args){
    
        AuthPluginManager authPluginManager = authPluginManager = new AuthPluginManager();
        authPluginManager.initAuthPlugins();
    }
}
