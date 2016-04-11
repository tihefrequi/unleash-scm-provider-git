package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import com.itemis.maven.plugins.unleash.scm.ScmProvider;

public class ScmProviderGit implements ScmProvider {
  private Git git;

  @Override
  public void initialize(String workingDirectory) {
    try {
      this.git = Git.open(new File(workingDirectory));
    } catch (IOException e) {
      throw new IllegalStateException("Could not initialize Git repository at " + workingDirectory, e);
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
    RevCommit revCommit;
    try {
      revCommit = this.git.log().call().iterator().next();
    } catch (GitAPIException e) {
      throw new IllegalStateException("Could not determine the last revision commit of the local repository.", e);
    }
    return revCommit.getName();
  }

  @Override
  public String getLatestRemoteRevision() {
    // TODO Auto-generated method stub
    return null;
  }
}
