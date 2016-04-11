package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.itemis.maven.plugins.unleash.scm.ScmProvider;

public class ScmProviderGit implements ScmProvider {

  public String getLocalRevision() {
    String revision = "";
    try {
      FileRepositoryBuilder builder = new FileRepositoryBuilder();
      Repository repo = builder
          .setGitDir(new File("C:/Users/Stanley/itemis/Projekte/VOEB/unleash-maven-plugin/repo-scm-provider-git/.git"))
          .readEnvironment().findGitDir().build();
      Git git = new Git(repo);
      RevCommit revCommit = git.log().call().iterator().next();
      revision = revCommit.getName();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return revision;
  }

  public static void main(String[] args) {
    System.out.println(new ScmProviderGit().getLocalRevision());
  }

  public String getLatestRemoteRevision() {
    // TODO Auto-generated method stub
    return null;
  }

}
