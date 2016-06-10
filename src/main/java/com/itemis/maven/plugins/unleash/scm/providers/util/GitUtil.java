package com.itemis.maven.plugins.unleash.scm.providers.util;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.itemis.maven.plugins.unleash.scm.ScmOperation;

public class GitUtil {
  public static final String TAG_NAME_PREFIX = "refs/tags/";
  public static final String HEADS_NAME_PREFIX = "refs/heads/";

  private Git git;

  public GitUtil(Git git) {
    this.git = git;
  }

  public boolean isDirty(Set<String> paths) throws ScmException {
    try {
      StatusCommand status = this.git.status();
      for (String path : paths) {
        status.addPath(path);
      }
      return !status.call().isClean();
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO, "Could not evaluate the status of the local repository.", e);
    }
  }

  public String getCurrentConnectionUrl() throws ScmException {
    String localBranchName = getCurrentBranchName();
    String remoteName = getRemoteName(localBranchName);
    return getConnectionUrlOfRemote(remoteName);
  }

  public String getCurrentBranchName() throws ScmException {
    try {
      String branch = this.git.getRepository().getBranch();
      if (branch == null) {
        throw new ScmException(ScmOperation.INFO,
            "Unable to determine name of currently checked out local branch. The repository must be corrupt.");
      }
      return branch;
    } catch (IOException e) {
      throw new ScmException(ScmOperation.INFO, "Unable to determine name of currently checked out local branch.", e);
    }
  }

  public String getRemoteBranchName(String localBranch) {
    return this.git.getRepository().getConfig().getString("branch", localBranch, "merge");
  }

  public String getRemoteName(String localBranch) {
    String remote = this.git.getRepository().getConfig().getString("branch", localBranch, "remote");
    if (remote == null) {
      // this can be the case if we are in detached head state
      Set<String> remotes = this.git.getRepository().getRemoteNames();
      remote = Iterables.getFirst(remotes, null);
    }
    return remote;
  }

  public String getConnectionUrlOfRemote(String remoteName) {
    if (remoteName != null) {
      return this.git.getRepository().getConfig().getString("remote", remoteName, "url");
    }
    return null;
  }

  public boolean hasLocalTag(String tagName) {
    try {
      List<Ref> tags = this.git.tagList().call();
      for (Ref tag : tags) {
        if (Objects.equal(tag.getName(), TAG_NAME_PREFIX + tagName)) {
          return true;
        }
      }
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO,
          "An error occurred while querying the local git repository for tag '" + tagName + "'.", e);
    }
    return false;
  }

  public boolean hasLocalBranch(String branchName) {
    try {
      List<Ref> branches = this.git.branchList().setListMode(ListMode.ALL).call();
      for (Ref branch : branches) {
        if (Objects.equal(branch.getName(), HEADS_NAME_PREFIX + branchName)) {
          return true;
        }
      }
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO,
          "An error occurred while querying the local git repository for branch '" + branchName + "'.", e);
    }
    return false;
  }

  public RevCommit resolveCommit(Optional<String> commitId, Optional<String> branchName) throws ScmException {
    try {
      LogCommand log = this.git.log();
      if (branchName.isPresent()) {
        String localBranchName = getCurrentBranchName();
        String remoteName = getRemoteName(localBranchName);
        log.add(this.git.getRepository().resolve(remoteName + "/" + branchName.get()));
      }
      if (!commitId.isPresent()) {
        log.setMaxCount(1);
      }
      Iterable<RevCommit> commits = log.call();

      if (commitId.isPresent()) {
        for (RevCommit commit : commits) {
          if (Objects.equal(commitId.get(), commit.getId().getName())) {
            return commit;
          }
        }
        throw new ScmException(ScmOperation.INFO, "Could not resolve commit with id '" + commitId.get()
            + (branchName.isPresent() ? "' for branch '" + branchName.get() + "'." : "'."));
      } else {
        return commits.iterator().next();
      }
    } catch (Exception e) {
      throw new ScmException(ScmOperation.INFO, "Could not resolve commit with id '" + commitId.or(Constants.HEAD)
          + (branchName.isPresent() ? "' for branch '" + branchName.get() + "'." : "'."), e);
    }
  }

  public List<RevCommit> resolveCommitRange(String from, String to) throws Exception {
    ObjectId fromId = this.git.getRepository().resolve(from);
    ObjectId toId = this.git.getRepository().resolve(to);
    Iterable<RevCommit> commits = this.git.log().addRange(fromId, toId).call();
    return Lists.newArrayList(commits);
  }
}
