package com.itemis.maven.plugins.unleash.scm.providers;

public class GitSshSessionFactoryTest {
  // TODO Refactor tests before releasing 2.0.0!
  // @Mock
  // private FS mockFS;
  // @Mock
  // private ScmProviderInitialization mockInitialization;
  // @Mock
  // private Logger mockLogger;
  // private GitSshSessionFactory sessionFactory;
  // private JSch sshClient;
  // private boolean connectorAvailable;
  //
  // @Before
  // public void before() {
  // MockitoAnnotations.initMocks(this);
  // // reset this before every tests b/c it's static :(
  // JSch.setConfig(new Hashtable<Object, Object>());
  //
  // this.sessionFactory = new GitSshSessionFactory(this.mockInitialization, this.mockLogger) {
  // @Override
  // boolean isConnectorAvailable() {
  // return GitSshSessionFactoryTest.this.connectorAvailable;
  // }
  // };
  // }
  //
  // @Test
  // public void testNoPassphraseOrAgent() throws Exception {
  // givenNoPassphraseIsPresent();
  // givenNoAgentConnectorIsAvailable();
  // whenCreateSshClient();
  // thenIdentityRepositoryIsLocal();
  // thenPreferredAuthenticationIsNotPublicKey();
  // }
  //
  // @Test
  // public void testUsePassphrase() throws Exception {
  // givenAPassphraseIsPresent();
  // givenAgentConnectorAvailable();
  // whenCreateSshClient();
  // thenIdentityRepositoryIsLocal();
  // }
  //
  // @Test
  // public void testUseSshAgent() throws Exception {
  // givenNoPassphraseIsPresent();
  // givenAgentConnectorAvailable();
  // whenCreateSshClient();
  // thenPreferredAuthenticationIsPublicKey();
  // thenIdentityRepositoryIsRemote();
  // }
  //
  // private void givenAgentConnectorAvailable() {
  // this.connectorAvailable = true;
  // }
  //
  // private void givenAPassphraseIsPresent() {
  // when(this.mockInitialization.getSshPrivateKeyPassphrase()).thenReturn(Optional.of("passphrase"));
  // }
  //
  // private void givenNoAgentConnectorIsAvailable() {
  // this.connectorAvailable = false;
  // }
  //
  // private void givenNoPassphraseIsPresent() {
  // when(this.mockInitialization.getSshPrivateKeyPassphrase()).thenReturn(Optional.<String> absent());
  // }
  //
  // private void thenIdentityRepositoryIsLocal() {
  // assertFalse(this.sshClient.getIdentityRepository() instanceof RemoteIdentityRepository);
  // }
  //
  // private void thenIdentityRepositoryIsRemote() {
  // assertTrue(this.sshClient.getIdentityRepository() instanceof RemoteIdentityRepository);
  // }
  //
  // private void thenPreferredAuthenticationIsNotPublicKey() {
  // // if this isn't explicitly set to 'publickey', no passphrase or agent were found
  // assertNotEquals(PUBLIC_KEY, JSch.getConfig(PREFERRED_AUTHENTICATIONS));
  // }
  //
  // private void thenPreferredAuthenticationIsPublicKey() {
  // assertEquals(PUBLIC_KEY, JSch.getConfig(PREFERRED_AUTHENTICATIONS));
  // }
  //
  // private void whenCreateSshClient() throws Exception {
  // when(this.mockFS.userHome()).thenReturn(null);
  // this.sshClient = this.sessionFactory.createDefaultJSch(this.mockFS);
  // }
}
