package com.alibaba.nacos.config.server.utils;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

public class PropertiesEncrypt {
  private String prefix = "ENC(";
  private String suffix = ")";
  private StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();

  public PropertiesEncrypt(String password) {
    SimpleStringPBEConfig config = new SimpleStringPBEConfig();
    config.setPassword(password);
    this.encryptor.setConfig(config);
  }

  public String encrypt(String value) {
    return this.prefix + this.encryptor.encrypt(value.trim()) + this.suffix;
  }

  public String decrypt(String value) {
    if (value != null) {
      return value.startsWith(this.prefix) && value.endsWith(this.suffix) ? this.encryptor.decrypt(this.unwrapEncryptedValue(value)) : value;
    } else {
      return null;
    }
  }

  private String unwrapEncryptedValue(String value) {
    return value.trim().substring(this.prefix.length(), value.trim().length() - 1);
  }

  public static PropertiesEncryptorBuilder builder() {
    return new PropertiesEncryptorBuilder();
  }

  public static final class PropertiesEncryptorBuilder {
    private String pd = "nc";

    public PropertiesEncryptorBuilder() {
    }

    public PropertiesEncryptorBuilder password(String pd) {
      this.pd = pd;
      return this;
    }

    public PropertiesEncrypt build() {
      return new PropertiesEncrypt(this.pd);
    }
  }
}
