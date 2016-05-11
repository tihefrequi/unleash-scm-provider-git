package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;

import com.itemis.maven.plugins.unleash.scm.ScmProvider;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;

public class Main {
  public static void main(String[] args) throws Exception {
    ScmProvider p = new ScmProviderGit();
    p.initialize(new File("C:/Users/Stanley/itemis/p1/git/test-repo/.git"));

    try {
      CommitRequest request = CommitRequest.builder().setMessage("test").push().build();
      String result = p.commit(request);
      System.out.println(result);
    } finally {
      p.close();
    }

    // FileRepositoryBuilder builder = new FileRepositoryBuilder();
    // Repository repo = builder.findGitDir(new File("C:/Users/Stanley/itemis/p1/git/test-repo/.git")).build();
    // Git git = Git.wrap(repo);
    // PersonIdent pi = new PersonIdent(git.getRepository());
    //
    // // Status status = git.status().call();
    // // if (!status.getUntracked().isEmpty()) {
    // // System.out.println(status.getUntracked());
    // // }
    // // RevCommit commit = git.commit().setMessage("test").setAll(true).setCommitter(pi).call();
    // // System.out.println(commit.getId());
    // Iterable<PushResult> call = git.push()
    // .setCredentialsProvider(new UsernamePasswordCredentialsProvider("shillner", "2crim-OG")).call();
    // for (PushResult pr : call) {
    // System.out.println(pr);
    // }
  }
}
