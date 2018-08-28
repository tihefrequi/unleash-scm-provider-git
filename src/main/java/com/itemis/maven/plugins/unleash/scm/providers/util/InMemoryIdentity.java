package com.itemis.maven.plugins.unleash.scm.providers.util;

import com.jcraft.jsch.Identity;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;

public class InMemoryIdentity implements Identity {
  private KeyPair keyPair;
  private String identity;

  public static InMemoryIdentity newInstance(String name, String privateKey, JSch jsch) throws JSchException {
    KeyPair kpair = KeyPair.load(jsch, privateKey.getBytes(), null);
    return new InMemoryIdentity(jsch, name, kpair);
  }

  private InMemoryIdentity(JSch jsch, String name, KeyPair keyPair) throws JSchException {
    this.identity = name;
    this.keyPair = keyPair;
  }

  @Override
  public boolean setPassphrase(byte[] passphrase) throws JSchException {
    return this.keyPair.decrypt(passphrase);
  }

  @Override
  public byte[] getPublicKeyBlob() {
    return this.keyPair.getPublicKeyBlob();
  }

  @Override
  public byte[] getSignature(byte[] data) {
    return this.keyPair.getSignature(data);
  }

  @Override
  public boolean decrypt() {
    throw new RuntimeException("not implemented");
  }

  @Override
  public String getAlgName() {
    String algName;
    switch (this.keyPair.getKeyType()) {
      case KeyPair.DSA:
        algName = "ssh-dss";
        break;
      case KeyPair.RSA:
        algName = "ssh-rsa";
        break;
      default:
        algName = "unknown";
        break;
    }
    return algName;
  }

  @Override
  public String getName() {
    return this.identity;
  }

  @Override
  public boolean isEncrypted() {
    return this.keyPair.isEncrypted();
  }

  @Override
  public void clear() {
    this.keyPair.dispose();
    this.keyPair = null;
  }

  public KeyPair getKeyPair() {
    return this.keyPair;
  }
}
