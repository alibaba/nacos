package com.alibaba.nacos.config.server.flyway;

import com.alibaba.nacos.config.server.utils.PropertiesEncrypt;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;

public class FlywayInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(FlywayInitializer.class);

  @Override
  public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
    Optional<String> optional = Optional
        .ofNullable(configurableApplicationContext.getEnvironment().getProperty("spring.flyway.enabled"));
    if (optional.isPresent() && optional.get().toLowerCase().equals("true")) {
      log.info("Flyway Initializer");
      String url = configurableApplicationContext.getEnvironment().getProperty("db.url[0]");
      String user = configurableApplicationContext.getEnvironment().getProperty("db.user[0]");
      if (user == null || user.trim().length() == 0) {
        user = configurableApplicationContext.getEnvironment().getProperty("db.user");
      }
      String password = configurableApplicationContext.getEnvironment().getProperty("db.password[0]");
      if (password == null || password.trim().length() == 0) {
        password = configurableApplicationContext.getEnvironment().getProperty("db.password");
      }

      // 如果是密文，则返回明文
      password = tryDecryptPassword(configurableApplicationContext.getEnvironment().getProperty("jasypt.encryptor.password"), password);

      String locations = configurableApplicationContext.getEnvironment().getProperty("spring.flyway.locations");
      String baselineVersion = configurableApplicationContext.getEnvironment().getProperty("spring.flyway.baseline-version");
      String table = configurableApplicationContext.getEnvironment().getProperty("spring.flyway.table");

      Flyway flyway = Flyway.configure().locations(locations).table(table).baselineOnMigrate(true)
          .cleanDisabled(true).baselineVersion(baselineVersion).dataSource(url, user, password).load();
      flyway.migrate();
    } else {
      log.info("Ignore Flyway initializer, No configuration spring.flyway.enabled=true");
    }
  }

  private String tryDecryptPassword(String password, String value) {
    PropertiesEncrypt encryptor = PropertiesEncrypt.builder().password(password).build();
    return encryptor.decrypt(value);
  }

}
