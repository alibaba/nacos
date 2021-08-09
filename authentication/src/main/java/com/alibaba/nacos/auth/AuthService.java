package authentication.src.main.java.com.alibaba.nacos.auth;

import authentication.src.main.java.com.alibaba.nacos.auth.authContext.AuthContextBuilder;

public interface AuthService {
    
    // HTTP
    Boolean login(AuthContextBuilder authContext);//throws AccessException;
    
    
    // get username and password from request
    Boolean validation(Object object1, Object object2);
    
    //
    
    
    
    
}
