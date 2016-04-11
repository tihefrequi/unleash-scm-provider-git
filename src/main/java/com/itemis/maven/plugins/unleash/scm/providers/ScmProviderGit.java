package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.itemis.maven.plugins.unleash.scm.ScmProvider;
import com.itemis.maven.plugins.unleash.scm.ScmProviderType;

@ScmProviderType("git")
public class ScmProviderGit implements ScmProvider {
  private Git git;

  @Override
  public void initialize(File workingDirectory) {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    try {
      Repository repo = builder.findGitDir(workingDirectory).build();
      this.git = Git.wrap(repo);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void close() {
    if (this.git != null) {
      this.git.close();
    }
  }

  @Override
  public String getLocalRevision() {
    try {
      RevCommit revCommit = this.git.log().call().iterator().next();
      return revCommit.getName();
    } catch (GitAPIException e) {
      throw new IllegalStateException("Could not determine the last revision commit of the local repository.", e);
    }
  }

  @Override
  public String getLatestRemoteRevision() {
    // TODO Auto-generated method stub
    return null;
  }
}
