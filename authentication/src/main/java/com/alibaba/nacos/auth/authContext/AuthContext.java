package authentication.src.main.java.com.alibaba.nacos.auth.authContext;

import com.alibaba.nacos.api.config.filter.IConfigContext;

import java.util.HashMap;
import java.util.Map;

public class AuthContext {
        
        private final Map<String, Object> param = new HashMap<String, Object>();
        
        public Object getParameter(String key) {
            return param.get(key);
        }
        
        public void setParameter(String key, Object value) {
            param.put(key, value);
        }
        
    
}
