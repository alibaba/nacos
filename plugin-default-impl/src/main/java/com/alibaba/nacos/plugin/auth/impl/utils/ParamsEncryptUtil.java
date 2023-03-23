package com.alibaba.nacos.plugin.auth.impl.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ParamsEncryptUtil {

  private static final String PARAMS_ENCODE_KEY = "PARAMS_ENCODE_KEY";
  private String encodeKey = "ncncncncncncncnc";
  private static volatile ParamsEncryptUtil instance;

  public static ParamsEncryptUtil getInstance() {
    if (instance == null) {
      synchronized (ParamsEncryptUtil.class) {
        if (instance == null) {
          instance = new ParamsEncryptUtil();
        }
      }
    }
    return instance;
  }

  public ParamsEncryptUtil() {
    String encodeKey = System.getenv(PARAMS_ENCODE_KEY);
    if (encodeKey != null && encodeKey.trim().length() > 0) {
      this.encodeKey = encodeKey;
    }
  }

  public String decryptAES(String value) {
    try {
      value = value.replaceAll(" ", "+");
      IvParameterSpec iv = new IvParameterSpec(encodeKey.getBytes(StandardCharsets.UTF_8));
      SecretKeySpec skeySpec = new SecretKeySpec(encodeKey.getBytes(StandardCharsets.UTF_8), "AES");
      Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
      cipher.init(2, skeySpec, iv);
      byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(value));
      return (new String(decrypted, StandardCharsets.UTF_8)).trim();
    } catch (Exception var7) {
      throw new RuntimeException(var7);
    }
  }

  public String encrypAES(String value) {
    try {
      IvParameterSpec iv = new IvParameterSpec(encodeKey.getBytes(StandardCharsets.UTF_8));
      SecretKeySpec skeySpec = new SecretKeySpec(encodeKey.getBytes(StandardCharsets.UTF_8), "AES");
      Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
      cipher.init(1, skeySpec, iv);
      byte[] valueAfterPadding = this.paddingDataWithZero(value.getBytes(StandardCharsets.UTF_8), 16);
      byte[] encrypted = cipher.doFinal(valueAfterPadding);
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (Exception var8) {
      throw new RuntimeException(var8);
    }
  }

  private byte[] paddingDataWithZero(byte[] data, int blockSize) {
    int length = data.length;
    int remainLength = length % blockSize;
    if (remainLength > 0) {
      int newSize = length + blockSize - remainLength;
      if (newSize >= 0) {
        byte[] newArray = new byte[newSize];
        if (newSize > 0 && data.length > 0) {
          System.arraycopy(data, 0, newArray, 0, Math.min(data.length, newSize));
        }
        return newArray;
      }
    }
    return data;
  }
}