package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.DeleteTagCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListTagCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.itemis.maven.plugins.unleash.scm.ScmOperation;
import com.itemis.maven.plugins.unleash.scm.ScmProvider;
import com.itemis.maven.plugins.unleash.scm.annotations.ScmProviderType;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;
import com.itemis.maven.plugins.unleash.scm.requests.TagRequest;

@ScmProviderType("git")
public class ScmProviderGit implements ScmProvider {
  private Git git;
  private PersonIdent personIdent;

  @Override
  public void initialize(File workingDirectory) {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    try {
      Repository repo = builder.findGitDir(workingDirectory).build();
      this.git = Git.wrap(repo);
      this.personIdent = new PersonIdent(repo);
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
  public String commit(CommitRequest request) throws ScmException {
    CommitCommand commit = this.git.commit();
    commit.setMessage(request.getMessage());
    if (request.commitAllChanges()) {
      commit.setAll(true);
    } else {
      for (String path : request.getPathsToCommit().get()) {
        commit.setOnly(path);
      }
    }
    commit.setCommitter(this.personIdent);
    try {
      RevCommit result = commit.call();
      if (request.push()) {
        push();
      }
      return result.getName();
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.DELETE_TAG, "Could not commit chanhes of local repository.", e);
    }
  }

  @Override
  public void push() throws ScmException {
    PushCommand push = this.git.push();
    // TODO implement!
  }

  @Override
  public void update() throws ScmException {
    // TODO implement!
  }

  @Override
  public void tag(TagRequest request) throws ScmException {
    // TODO tag and push!
    TagCommand tag = this.git.tag();
    tag.setName(request.getTagName());
    tag.setAnnotated(true);
    tag.setMessage(request.getMessage());
    tag.setTagger(this.personIdent);

    String revision = null;
    if (request.useWorkingCopy()) {
      revision = "HEAD";

      // TODO commit
    } else {
      revision = request.getRevision().get();
      try {
        RevWalk walk = new RevWalk(this.git.getRepository());
        ObjectId objectId = this.git.getRepository().resolve(revision);
        RevObject revObject = walk.parseAny(objectId);
        tag.setObjectId(revObject);
      } catch (Exception e) {
        // TODO more detailed exception analyzing
        throw new ScmException(ScmOperation.TAG, "Unable to determine revision to tag.");
      }
    }

    try {
      Ref ref = tag.call();
    } catch (Throwable t) {
      t.printStackTrace();
    }

    if (request.useWorkingCopy() && !request.commitBeforeTagging()) {
      // TODO remove temp commit after tagging
    }
  }

  @Override
  public boolean hasTag(String tagName) {
    ListTagCommand tagList = this.git.tagList();
    try {
      List<Ref> tags = tagList.call();
      for (Ref tag : tags) {
        if (tag.getName().endsWith("/" + tagName)) {
          return true;
        }
      }
      return false;
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.DELETE_TAG, "Could not delete Git tag from local repository.", e);
    }
  }

  @Override
  public void deleteTag(String tagName) throws ScmException {
    ListTagCommand tagList = this.git.tagList();
    try {
      String effectiveTagName = tagName;
      List<Ref> tags = tagList.call();
      for (Ref tag : tags) {
        if (tag.getName().endsWith("/" + tagName)) {
          effectiveTagName = tag.getName();
          break;
        }
      }

      DeleteTagCommand tagDelete = this.git.tagDelete();
      tagDelete.setTags(effectiveTagName);
      tagDelete.call();
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.DELETE_TAG, "Could not delete Git tag from local repository.", e);
    }
    // TODO push
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
    // TODO implement remote fetching and revision retrieval
    return getLocalRevision();
  }

  @Override
  public String calculateTagConnectionString(String currentConnectionString, String tagName) {
    return currentConnectionString;
  }

  @Override
  public String calculateBranchConnectionString(String currentConnectionString, String branchName) {
    return currentConnectionString;
  }

  @Override
  public boolean isTagInfoIncludedInConnection() {
    return false;
  }
}
