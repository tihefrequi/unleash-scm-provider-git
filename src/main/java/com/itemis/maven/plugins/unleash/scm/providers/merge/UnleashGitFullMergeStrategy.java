package com.itemis.maven.plugins.unleash.scm.providers.merge;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;

import com.itemis.maven.plugins.unleash.scm.merge.MergeClient;

public class UnleashGitFullMergeStrategy extends MergeStrategy {
  private MergeClient mergeClient;

  public UnleashGitFullMergeStrategy(MergeClient mergeClient) {
    super();
    this.mergeClient = mergeClient;
  }

  @Override
  public String getName() {
    return "unleash";
  }

  @Override
  public Merger newMerger(Repository db) {
    return new UnleashGitMerger(db, this.mergeClient);
  }

  @Override
  public Merger newMerger(Repository db, boolean inCore) {
    return new UnleashGitMerger(db, inCore, this.mergeClient);
  }
}
