package com.itemis.maven.plugins.unleash.scm.providers;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.itemis.maven.plugins.unleash.scm.ScmProviderInitialization;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;
import com.jcraft.jsch.agentproxy.connector.PageantConnector;
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector;
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory;

class GitSshSessionFactory extends JschConfigSessionFactory {

  private static final String OS = System.getProperty("os.name").toLowerCase();

  static final String PREFERRED_AUTHENTICATIONS = "PreferredAuthentications";
  static final String PUBLIC_KEY = "publickey";

  private final ScmProviderInitialization initialization;
  private final Logger logger;

  GitSshSessionFactory(ScmProviderInitialization initialization, Logger logger) {
    this.initialization = initialization;
    this.logger = logger;
  }

  @Override
  protected void configure(Host hc, Session session) {
  }

  @Override
  protected JSch createDefaultJSch(FS fs) throws JSchException {
    JSch jsch = super.createDefaultJSch(fs);

    /*
     * it appears that jsch can only work with a single 'IdentityRepository', so we default to
     * using the passphrase if it is passed, otherwise we fall back to trying the ssh agent
     */
    if (this.initialization.getSshPrivateKeyPassphrase().isPresent()) {
      String passphrase = this.initialization.getSshPrivateKeyPassphrase().get();
      @SuppressWarnings("unchecked")
      List<String> identityNames = Lists.newArrayList(jsch.getIdentityNames());

      jsch.removeAllIdentity();
      for (String name : identityNames) {
        jsch.addIdentity(name, passphrase);
      }
    } else {
      Connector sshAgentConnector = getAgentConnector();
      if (sshAgentConnector != null) {
        JSch.setConfig(PREFERRED_AUTHENTICATIONS, PUBLIC_KEY);

        IdentityRepository identityRepository = new RemoteIdentityRepository(sshAgentConnector);
        jsch.setIdentityRepository(identityRepository);
      }
    }

    return jsch;
  }

  @VisibleForTesting
  boolean isConnectorAvailable() {
    return PageantConnector.isConnectorAvailable() || SSHAgentConnector.isConnectorAvailable();
  }

  private Connector getAgentConnector() {
    Connector connector = null;

    if (isConnectorAvailable()) {
      try {
        connector = OS.indexOf("win") >= 0 ? getWindowsConnector() : getUnixAgentConnector();
      } catch (AgentProxyException e) {
        this.logger.warning("failed to create connector to ssh agent: " + e.getMessage());
      }
    }

    return connector;
  }

  private Connector getWindowsConnector() throws AgentProxyException {
    return new PageantConnector();
  }

  private Connector getUnixAgentConnector() throws AgentProxyException {
    return new SSHAgentConnector(new JNAUSocketFactory());
  }
}
