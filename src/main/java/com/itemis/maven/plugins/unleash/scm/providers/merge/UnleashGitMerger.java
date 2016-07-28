/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>,
 * Copyright (C) 2010-2012, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2012, Research In Motion Limited
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.itemis.maven.plugins.unleash.scm.providers.merge;

import static org.eclipse.jgit.lib.Constants.CHARACTER_ENCODING;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.IndexWriteException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeAlgorithm;
import org.eclipse.jgit.merge.MergeChunk.ConflictState;
import org.eclipse.jgit.merge.MergeFormatter;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.merge.RecursiveMerger;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.TemporaryBuffer;

import com.google.common.io.Closeables;
import com.itemis.maven.plugins.unleash.scm.merge.MergeClient;
import com.itemis.maven.plugins.unleash.scm.providers.ScmProviderGit;

/**
 * This is a copy of the {@link ResolveMerger} class and is meant as a temporary solution until the
 * {@link ScmProviderGit} is fully implemented. After that the merger will be re-implemented to better match the
 * concepts we require.
 */
// TODO re-implement this merger or look at how to extend any existing merger implementation and modify required parts
// only.
public class UnleashGitMerger extends ResolveMerger {
  /**
   * The tree walk which we'll iterate over to merge entries.
   *
   * @since 3.4
   */
  protected NameConflictTreeWalk tw;

  /**
   * string versions of a list of commit SHA1s
   *
   * @since 3.0
   */
  protected String commitNames[];

  /**
   * Index of the base tree within the {@link #tw tree walk}.
   *
   * @since 3.4
   */
  protected static final int T_BASE = 0;

  /**
   * Index of our tree in withthe {@link #tw tree walk}.
   *
   * @since 3.4
   */
  protected static final int T_OURS = 1;

  /**
   * Index of their tree within the {@link #tw tree walk}.
   *
   * @since 3.4
   */
  protected static final int T_THEIRS = 2;

  /**
   * Index of the index tree within the {@link #tw tree walk}.
   *
   * @since 3.4
   */
  protected static final int T_INDEX = 3;

  /**
   * Index of the working directory tree within the {@link #tw tree walk}.
   *
   * @since 3.4
   */
  protected static final int T_FILE = 4;

  /**
   * Builder to update the cache during this merge.
   *
   * @since 3.4
   */
  protected DirCacheBuilder builder;

  /**
   * merge result as tree
   *
   * @since 3.0
   */
  protected ObjectId resultTree;

  /**
   * Paths that could not be merged by this merger because of an unsolvable
   * conflict.
   *
   * @since 3.4
   */
  protected List<String> unmergedPaths = new ArrayList<String>();

  /**
   * Files modified during this merge operation.
   *
   * @since 3.4
   */
  protected List<String> modifiedFiles = new LinkedList<String>();

  /**
   * If the merger has nothing to do for a file but check it out at the end of
   * the operation, it can be added here.
   *
   * @since 3.4
   */
  protected Map<String, DirCacheEntry> toBeCheckedOut = new HashMap<String, DirCacheEntry>();

  /**
   * Paths in this list will be deleted from the local copy at the end of the
   * operation.
   *
   * @since 3.4
   */
  protected List<String> toBeDeleted = new ArrayList<String>();

  /**
   * Low-level textual merge results. Will be passed on to the callers in case
   * of conflicts.
   *
   * @since 3.4
   */
  protected Map<String, MergeResult<? extends Sequence>> mergeResults = new HashMap<String, MergeResult<? extends Sequence>>();

  /**
   * Paths for which the merge failed altogether.
   *
   * @since 3.4
   */
  protected Map<String, MergeFailureReason> failingPaths = new HashMap<String, MergeFailureReason>();

  /**
   * Updated as we merge entries of the tree walk. Tells us whether we should
   * recurse into the entry if it is a subtree.
   *
   * @since 3.4
   */
  protected boolean enterSubtree;

  /**
   * Set to true if this merge should work in-memory. The repos dircache and
   * workingtree are not touched by this method. Eventually needed files are
   * created as temporary files and a new empty, in-memory dircache will be
   * used instead the repo's one. Often used for bare repos where the repo
   * doesn't even have a workingtree and dircache.
   *
   * @since 3.0
   */
  protected boolean inCore;

  /**
   * Set to true if this merger should use the default dircache of the
   * repository and should handle locking and unlocking of the dircache. If
   * this merger should work in-core or if an explicit dircache was specified
   * during construction then this field is set to false.
   *
   * @since 3.0
   */
  protected boolean implicitDirCache;

  /**
   * Directory cache
   *
   * @since 3.0
   */
  protected DirCache dircache;

  /**
   * The iterator to access the working tree. If set to <code>null</code> this
   * merger will not touch the working tree.
   *
   * @since 3.0
   */
  protected WorkingTreeIterator workingTreeIterator;

  /**
   * our merge algorithm
   *
   * @since 3.0
   */
  protected MergeAlgorithm mergeAlgorithm;

  private MergeClient mergeClient;

  /**
   * @param local
   * @param inCore
   */
  protected UnleashGitMerger(Repository local, boolean inCore, MergeClient mergeClient) {
    super(local);
    this.mergeClient = mergeClient;
    SupportedAlgorithm diffAlg = local.getConfig().getEnum(ConfigConstants.CONFIG_DIFF_SECTION, null,
        ConfigConstants.CONFIG_KEY_ALGORITHM, SupportedAlgorithm.HISTOGRAM);
    this.mergeAlgorithm = new MergeAlgorithm(DiffAlgorithm.getAlgorithm(diffAlg));
    this.commitNames = new String[] { "BASE", "OURS", "THEIRS" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    this.inCore = inCore;

    if (inCore) {
      this.implicitDirCache = false;
      this.dircache = DirCache.newInCore();
    } else {
      this.implicitDirCache = true;
    }
  }

  /**
   * @param local
   */
  protected UnleashGitMerger(Repository local, MergeClient mergeClient) {
    this(local, false, mergeClient);
  }

  @Override
  protected boolean mergeImpl() throws IOException {
    if (this.implicitDirCache) {
      this.dircache = getRepository().lockDirCache();
    }

    try {
      return mergeTrees(mergeBase(), this.sourceTrees[0], this.sourceTrees[1], false);
    } finally {
      if (this.implicitDirCache) {
        this.dircache.unlock();
      }
    }
  }

  private void checkout() throws NoWorkTreeException, IOException {
    // Iterate in reverse so that "folder/file" is deleted before
    // "folder". Otherwise this could result in a failing path because
    // of a non-empty directory, for which delete() would fail.
    for (int i = this.toBeDeleted.size() - 1; i >= 0; i--) {
      String fileName = this.toBeDeleted.get(i);
      File f = new File(this.db.getWorkTree(), fileName);
      if (!f.delete()) {
        if (!f.isDirectory()) {
          this.failingPaths.put(fileName, MergeFailureReason.COULD_NOT_DELETE);
        }
      }
      this.modifiedFiles.add(fileName);
    }
    for (Map.Entry<String, DirCacheEntry> entry : this.toBeCheckedOut.entrySet()) {
      DirCacheCheckout.checkoutEntry(this.db, entry.getValue(), this.reader);
      this.modifiedFiles.add(entry.getKey());
    }
  }

  /**
   * Reverts the worktree after an unsuccessful merge. We know that for all
   * modified files the old content was in the old index and the index
   * contained only stage 0. In case if inCore operation just clear the
   * history of modified files.
   *
   * @throws IOException
   * @throws CorruptObjectException
   * @throws NoWorkTreeException
   * @since 3.4
   */
  @Override
  protected void cleanUp() throws NoWorkTreeException, CorruptObjectException, IOException {
    if (this.inCore) {
      this.modifiedFiles.clear();
      return;
    }

    DirCache dc = this.db.readDirCache();
    Iterator<String> mpathsIt = this.modifiedFiles.iterator();
    while (mpathsIt.hasNext()) {
      String mpath = mpathsIt.next();
      DirCacheEntry entry = dc.getEntry(mpath);
      if (entry != null) {
        DirCacheCheckout.checkoutEntry(this.db, entry, this.reader);
      }
      mpathsIt.remove();
    }
  }

  /**
   * adds a new path with the specified stage to the index builder
   *
   * @param path
   * @param p
   * @param stage
   * @param lastMod
   * @param len
   * @return the entry which was added to the index
   */
  private DirCacheEntry add(byte[] path, CanonicalTreeParser p, int stage, long lastMod, long len) {
    if (p != null && !p.getEntryFileMode().equals(FileMode.TREE)) {
      DirCacheEntry e = new DirCacheEntry(path, stage);
      e.setFileMode(p.getEntryFileMode());
      e.setObjectId(p.getEntryObjectId());
      e.setLastModified(lastMod);
      e.setLength(len);
      this.builder.add(e);
      return e;
    }
    return null;
  }

  /**
   * adds a entry to the index builder which is a copy of the specified
   * DirCacheEntry
   *
   * @param e
   *          the entry which should be copied
   *
   * @return the entry which was added to the index
   */
  private DirCacheEntry keep(DirCacheEntry e) {
    DirCacheEntry newEntry = new DirCacheEntry(e.getPathString(), e.getStage());
    newEntry.setFileMode(e.getFileMode());
    newEntry.setObjectId(e.getObjectId());
    newEntry.setLastModified(e.getLastModified());
    newEntry.setLength(e.getLength());
    this.builder.add(newEntry);
    return newEntry;
  }

  /**
   * Processes one path and tries to merge. This method will do all do all
   * trivial (not content) merges and will also detect if a merge will fail.
   * The merge will fail when one of the following is true
   * <ul>
   * <li>the index entry does not match the entry in ours. When merging one
   * branch into the current HEAD, ours will point to HEAD and theirs will
   * point to the other branch. It is assumed that the index matches the HEAD
   * because it will only not match HEAD if it was populated before the merge
   * operation. But the merge commit should not accidentally contain
   * modifications done before the merge. Check the <a href=
   * "http://www.kernel.org/pub/software/scm/git/docs/git-read-tree.html#_3_way_merge"
   * >git read-tree</a> documentation for further explanations.</li>
   * <li>A conflict was detected and the working-tree file is dirty. When a
   * conflict is detected the content-merge algorithm will try to write a
   * merged version into the working-tree. If the file is dirty we would
   * override unsaved data.</li>
   * </ul>
   *
   * @param base
   *          the common base for ours and theirs
   * @param ours
   *          the ours side of the merge. When merging a branch into the
   *          HEAD ours will point to HEAD
   * @param theirs
   *          the theirs side of the merge. When merging a branch into the
   *          current HEAD theirs will point to the branch which is merged
   *          into HEAD.
   * @param index
   *          the index entry
   * @param work
   *          the file in the working tree
   * @param ignoreConflicts
   *          see
   *          {@link UnleashGitMerger#mergeTrees(AbstractTreeIterator, RevTree, RevTree, boolean)}
   * @return <code>false</code> if the merge will fail because the index entry
   *         didn't match ours or the working-dir file was dirty and a
   *         conflict occurred
   * @throws MissingObjectException
   * @throws IncorrectObjectTypeException
   * @throws CorruptObjectException
   * @throws IOException
   * @since 3.5
   */
  @Override
  protected boolean processEntry(CanonicalTreeParser base, CanonicalTreeParser ours, CanonicalTreeParser theirs,
      DirCacheBuildIterator index, WorkingTreeIterator work, boolean ignoreConflicts)
      throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
    this.enterSubtree = true;
    final int modeO = this.tw.getRawMode(T_OURS);
    final int modeT = this.tw.getRawMode(T_THEIRS);
    final int modeB = this.tw.getRawMode(T_BASE);

    if (modeO == 0 && modeT == 0 && modeB == 0) {
      // File is either untracked or new, staged but uncommitted
      return true;
    }

    if (isIndexDirty()) {
      return false;
    }

    DirCacheEntry ourDce = null;

    if (index == null || index.getDirCacheEntry() == null) {
      // create a fake DCE, but only if ours is valid. ours is kept only
      // in case it is valid, so a null ourDce is ok in all other cases.
      if (nonTree(modeO)) {
        ourDce = new DirCacheEntry(this.tw.getRawPath());
        ourDce.setObjectId(this.tw.getObjectId(T_OURS));
        ourDce.setFileMode(this.tw.getFileMode(T_OURS));
      }
    } else {
      ourDce = index.getDirCacheEntry();
    }

    if (nonTree(modeO) && nonTree(modeT) && this.tw.idEqual(T_OURS, T_THEIRS)) {
      // OURS and THEIRS have equal content. Check the file mode
      if (modeO == modeT) {
        // content and mode of OURS and THEIRS are equal: it doesn't
        // matter which one we choose. OURS is chosen. Since the index
        // is clean (the index matches already OURS) we can keep the existing one
        keep(ourDce);
        // no checkout needed!
        return true;
      } else {
        // same content but different mode on OURS and THEIRS.
        // Try to merge the mode and report an error if this is
        // not possible.
        int newMode = mergeFileModes(modeB, modeO, modeT);
        if (newMode != FileMode.MISSING.getBits()) {
          if (newMode == modeO) {
            // ours version is preferred
            keep(ourDce);
          } else {
            // the preferred version THEIRS has a different mode
            // than ours. Check it out!
            if (isWorktreeDirty(work, ourDce)) {
              return false;
            }
            // we know about length and lastMod only after we have written the new content.
            // This will happen later. Set these values to 0 for know.
            DirCacheEntry e = add(this.tw.getRawPath(), theirs, DirCacheEntry.STAGE_0, 0, 0);
            this.toBeCheckedOut.put(this.tw.getPathString(), e);
          }
          return true;
        } else {
          // FileModes are not mergeable. We found a conflict on modes.
          // For conflicting entries we don't know lastModified and length.
          add(this.tw.getRawPath(), base, DirCacheEntry.STAGE_1, 0, 0);
          add(this.tw.getRawPath(), ours, DirCacheEntry.STAGE_2, 0, 0);
          add(this.tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, 0, 0);
          this.unmergedPaths.add(this.tw.getPathString());
          this.mergeResults.put(this.tw.getPathString(), new MergeResult<RawText>(Collections.<RawText> emptyList()));
        }
        return true;
      }
    }

    if (modeB == modeT && this.tw.idEqual(T_BASE, T_THEIRS)) {
      // THEIRS was not changed compared to BASE. All changes must be in
      // OURS. OURS is chosen. We can keep the existing entry.
      if (ourDce != null) {
        keep(ourDce);
      }
      // no checkout needed!
      return true;
    }

    if (modeB == modeO && this.tw.idEqual(T_BASE, T_OURS)) {
      // OURS was not changed compared to BASE. All changes must be in
      // THEIRS. THEIRS is chosen.

      // Check worktree before checking out THEIRS
      if (isWorktreeDirty(work, ourDce)) {
        return false;
      }
      if (nonTree(modeT)) {
        // we know about length and lastMod only after we have written
        // the new content.
        // This will happen later. Set these values to 0 for know.
        DirCacheEntry e = add(this.tw.getRawPath(), theirs, DirCacheEntry.STAGE_0, 0, 0);
        if (e != null) {
          this.toBeCheckedOut.put(this.tw.getPathString(), e);
        }
        return true;
      } else {
        // we want THEIRS ... but THEIRS contains a folder or the
        // deletion of the path. Delete what's in the workingtree (the
        // workingtree is clean) but do not complain if the file is
        // already deleted locally. This complements the test in
        // isWorktreeDirty() for the same case.
        if (this.tw.getTreeCount() > T_FILE && this.tw.getRawMode(T_FILE) == 0) {
          return true;
        }
        this.toBeDeleted.add(this.tw.getPathString());
        return true;
      }
    }

    if (this.tw.isSubtree()) {
      // file/folder conflicts: here I want to detect only file/folder
      // conflict between ours and theirs. file/folder conflicts between
      // base/index/workingTree and something else are not relevant or
      // detected later
      if (nonTree(modeO) && !nonTree(modeT)) {
        if (nonTree(modeB)) {
          add(this.tw.getRawPath(), base, DirCacheEntry.STAGE_1, 0, 0);
        }
        add(this.tw.getRawPath(), ours, DirCacheEntry.STAGE_2, 0, 0);
        this.unmergedPaths.add(this.tw.getPathString());
        this.enterSubtree = false;
        return true;
      }
      if (nonTree(modeT) && !nonTree(modeO)) {
        if (nonTree(modeB)) {
          add(this.tw.getRawPath(), base, DirCacheEntry.STAGE_1, 0, 0);
        }
        add(this.tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, 0, 0);
        this.unmergedPaths.add(this.tw.getPathString());
        this.enterSubtree = false;
        return true;
      }

      // ours and theirs are both folders or both files (and treewalk
      // tells us we are in a subtree because of index or working-dir).
      // If they are both folders no content-merge is required - we can
      // return here.
      if (!nonTree(modeO)) {
        return true;
      }

      // ours and theirs are both files, just fall out of the if block
      // and do the content merge
    }

    if (nonTree(modeO) && nonTree(modeT)) {
      // Check worktree before modifying files
      if (isWorktreeDirty(work, ourDce)) {
        return false;
      }

      // Don't attempt to resolve submodule link conflicts
      if (isGitLink(modeO) || isGitLink(modeT)) {
        add(this.tw.getRawPath(), base, DirCacheEntry.STAGE_1, 0, 0);
        add(this.tw.getRawPath(), ours, DirCacheEntry.STAGE_2, 0, 0);
        add(this.tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, 0, 0);
        this.unmergedPaths.add(this.tw.getPathString());
        return true;
      }

      MergeResult<RawText> result = contentMerge(base, ours, theirs);
      // if (ignoreConflicts) {
      // result.setContainsConflicts(false);
      // }
      updateIndex(base, ours, theirs, result);
      if (result.containsConflicts() && !ignoreConflicts) {
        this.unmergedPaths.add(this.tw.getPathString());
      }
      this.modifiedFiles.add(this.tw.getPathString());
    } else if (modeO != modeT) {
      // OURS or THEIRS has been deleted
      if (modeO != 0 && !this.tw.idEqual(T_BASE, T_OURS) || modeT != 0 && !this.tw.idEqual(T_BASE, T_THEIRS)) {

        add(this.tw.getRawPath(), base, DirCacheEntry.STAGE_1, 0, 0);
        add(this.tw.getRawPath(), ours, DirCacheEntry.STAGE_2, 0, 0);
        DirCacheEntry e = add(this.tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, 0, 0);

        // OURS was deleted checkout THEIRS
        if (modeO == 0) {
          // Check worktree before checking out THEIRS
          if (isWorktreeDirty(work, ourDce)) {
            return false;
          }
          if (nonTree(modeT)) {
            if (e != null) {
              this.toBeCheckedOut.put(this.tw.getPathString(), e);
            }
          }
        }

        this.unmergedPaths.add(this.tw.getPathString());

        // generate a MergeResult for the deleted file
        this.mergeResults.put(this.tw.getPathString(), contentMerge(base, ours, theirs));
      }
    }
    return true;
  }

  // TODO this is the main adaption of this class -> merge delegation to the mergeclient
  private MergeResult<RawText> contentMerge(CanonicalTreeParser base, CanonicalTreeParser ours,
      CanonicalTreeParser theirs) throws IOException {
    InputStream localIn = getInputStream(ours.getEntryObjectId());
    InputStream remoteIn = getInputStream(theirs.getEntryObjectId());
    InputStream baseIn = getInputStream(base.getEntryObjectId());
    ByteArrayOutputStream resultOut = new ByteArrayOutputStream();

    this.mergeClient.merge(localIn, remoteIn, baseIn, resultOut);
    RawText resultText = new RawText(resultOut.toByteArray());

    List<RawText> sequences = new ArrayList<RawText>(1);
    sequences.add(resultText);
    MergeResult<RawText> result = new MergeResult<RawText>(sequences);
    result.add(0, 0, resultText.size(), ConflictState.NO_CONFLICT);

    return result;
  }

  private InputStream getInputStream(ObjectId id) throws IOException {
    if (id.equals(ObjectId.zeroId())) {
      return null;
    }
    return this.reader.open(id, OBJ_BLOB).openStream();
  }

  private boolean isIndexDirty() {
    if (this.inCore) {
      return false;
    }

    final int modeI = this.tw.getRawMode(T_INDEX);
    final int modeO = this.tw.getRawMode(T_OURS);

    // Index entry has to match ours to be considered clean
    final boolean isDirty = nonTree(modeI) && !(modeO == modeI && this.tw.idEqual(T_INDEX, T_OURS));
    if (isDirty) {
      this.failingPaths.put(this.tw.getPathString(), MergeFailureReason.DIRTY_INDEX);
    }
    return isDirty;
  }

  private boolean isWorktreeDirty(WorkingTreeIterator work, DirCacheEntry ourDce) throws IOException {
    if (work == null) {
      return false;
    }

    final int modeF = this.tw.getRawMode(T_FILE);
    final int modeO = this.tw.getRawMode(T_OURS);

    // Worktree entry has to match ours to be considered clean
    boolean isDirty;
    if (ourDce != null) {
      isDirty = work.isModified(ourDce, true, this.reader);
    } else {
      isDirty = work.isModeDifferent(modeO);
      if (!isDirty && nonTree(modeF)) {
        isDirty = !this.tw.idEqual(T_FILE, T_OURS);
      }
    }

    // Ignore existing empty directories
    if (isDirty && modeF == FileMode.TYPE_TREE && modeO == FileMode.TYPE_MISSING) {
      isDirty = false;
    }
    if (isDirty) {
      this.failingPaths.put(this.tw.getPathString(), MergeFailureReason.DIRTY_WORKTREE);
    }
    return isDirty;
  }

  /**
   * Updates the index after a content merge has happened. If no conflict has
   * occurred this includes persisting the merged content to the object
   * database. In case of conflicts this method takes care to write the
   * correct stages to the index.
   *
   * @param base
   * @param ours
   * @param theirs
   * @param result
   * @throws FileNotFoundException
   * @throws IOException
   */
  private void updateIndex(CanonicalTreeParser base, CanonicalTreeParser ours, CanonicalTreeParser theirs,
      MergeResult<RawText> result) throws FileNotFoundException, IOException {
    File mergedFile = !this.inCore ? writeMergedFile(result) : null;
    if (result.containsConflicts()) {
      // A conflict occurred, the file will contain conflict markers
      // the index will be populated with the three stages and the
      // workdir (if used) contains the halfway merged content.
      add(this.tw.getRawPath(), base, DirCacheEntry.STAGE_1, 0, 0);
      add(this.tw.getRawPath(), ours, DirCacheEntry.STAGE_2, 0, 0);
      add(this.tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, 0, 0);
      this.mergeResults.put(this.tw.getPathString(), result);
      return;
    }

    // No conflict occurred, the file will contain fully merged content.
    // The index will be populated with the new merged version.
    DirCacheEntry dce = new DirCacheEntry(this.tw.getPathString());

    // Set the mode for the new content. Fall back to REGULAR_FILE if
    // we can't merge modes of OURS and THEIRS.
    int newMode = mergeFileModes(this.tw.getRawMode(0), this.tw.getRawMode(1), this.tw.getRawMode(2));
    dce.setFileMode(newMode == FileMode.MISSING.getBits() ? FileMode.REGULAR_FILE : FileMode.fromBits(newMode));
    if (mergedFile != null) {
      long len = mergedFile.length();
      dce.setLastModified(mergedFile.lastModified());
      dce.setLength((int) len);
      InputStream is = new FileInputStream(mergedFile);
      try {
        dce.setObjectId(getObjectInserter().insert(OBJ_BLOB, len, is));
      } finally {
        is.close();
      }
    } else {
      dce.setObjectId(insertMergeResult(result));
    }
    this.builder.add(dce);
  }

  /**
   * Writes merged file content to the working tree.
   *
   * @param result
   *          the result of the content merge
   * @return the working tree file to which the merged content was written.
   * @throws FileNotFoundException
   * @throws IOException
   */
  private File writeMergedFile(MergeResult<RawText> result) throws FileNotFoundException, IOException {
    File workTree = this.db.getWorkTree();
    FS fs = this.db.getFS();
    File of = new File(workTree, this.tw.getPathString());
    File parentFolder = of.getParentFile();
    if (!fs.exists(parentFolder)) {
      parentFolder.mkdirs();
    }
    OutputStream os = null;
    try {
      os = new BufferedOutputStream(new FileOutputStream(of));
      new MergeFormatter().formatMerge(os, result, Arrays.asList(this.commitNames), CHARACTER_ENCODING);
    } finally {
      Closeables.close(os, true);
    }
    return of;
  }

  private ObjectId insertMergeResult(MergeResult<RawText> result) throws IOException {
    TemporaryBuffer.LocalFile buf = new TemporaryBuffer.LocalFile(this.db.getDirectory(), 10 << 20);
    try {
      new MergeFormatter().formatMerge(buf, result, Arrays.asList(this.commitNames), CHARACTER_ENCODING);
      buf.close();
      InputStream in = null;
      try {
        in = buf.openInputStream();
        return getObjectInserter().insert(OBJ_BLOB, buf.length(), in);
      } finally {
        Closeables.closeQuietly(in);
      }
    } finally {
      buf.destroy();
    }
  }

  /**
   * Try to merge filemodes. If only ours or theirs have changed the mode
   * (compared to base) we choose that one. If ours and theirs have equal
   * modes return that one. If also that is not the case the modes are not
   * mergeable. Return {@link FileMode#MISSING} int that case.
   *
   * @param modeB
   *          filemode found in BASE
   * @param modeO
   *          filemode found in OURS
   * @param modeT
   *          filemode found in THEIRS
   *
   * @return the merged filemode or {@link FileMode#MISSING} in case of a
   *         conflict
   */
  private int mergeFileModes(int modeB, int modeO, int modeT) {
    if (modeO == modeT) {
      return modeO;
    }
    if (modeB == modeO) {
      // Base equal to Ours -> chooses Theirs if that is not missing
      return modeT == FileMode.MISSING.getBits() ? modeO : modeT;
    }
    if (modeB == modeT) {
      // Base equal to Theirs -> chooses Ours if that is not missing
      return modeO == FileMode.MISSING.getBits() ? modeT : modeO;
    }
    return FileMode.MISSING.getBits();
  }

  private static RawText getRawText(ObjectId id, ObjectReader reader) throws IOException {
    if (id.equals(ObjectId.zeroId())) {
      return new RawText(new byte[] {});
    }
    return new RawText(reader.open(id, OBJ_BLOB).getCachedBytes());
  }

  private static boolean nonTree(final int mode) {
    return mode != 0 && !FileMode.TREE.equals(mode);
  }

  private static boolean isGitLink(final int mode) {
    return FileMode.GITLINK.equals(mode);
  }

  @Override
  public ObjectId getResultTreeId() {
    return this.resultTree == null ? null : this.resultTree.toObjectId();
  }

  /**
   * @param commitNames
   *          the names of the commits as they would appear in conflict
   *          markers
   */
  @Override
  public void setCommitNames(String[] commitNames) {
    this.commitNames = commitNames;
  }

  /**
   * @return the names of the commits as they would appear in conflict
   *         markers.
   */
  @Override
  public String[] getCommitNames() {
    return this.commitNames;
  }

  /**
   * @return the paths with conflicts. This is a subset of the files listed
   *         by {@link #getModifiedFiles()}
   */
  @Override
  public List<String> getUnmergedPaths() {
    return this.unmergedPaths;
  }

  /**
   * @return the paths of files which have been modified by this merge. A
   *         file will be modified if a content-merge works on this path or if
   *         the merge algorithm decides to take the theirs-version. This is a
   *         superset of the files listed by {@link #getUnmergedPaths()}.
   */
  @Override
  public List<String> getModifiedFiles() {
    return this.modifiedFiles;
  }

  /**
   * @return a map which maps the paths of files which have to be checked out
   *         because the merge created new fully-merged content for this file
   *         into the index. This means: the merge wrote a new stage 0 entry
   *         for this path.
   */
  @Override
  public Map<String, DirCacheEntry> getToBeCheckedOut() {
    return this.toBeCheckedOut;
  }

  /**
   * @return the mergeResults
   */
  @Override
  public Map<String, MergeResult<? extends Sequence>> getMergeResults() {
    return this.mergeResults;
  }

  /**
   * @return lists paths causing this merge to fail (not stopped because of a
   *         conflict). <code>null</code> is returned if this merge didn't
   *         fail.
   */
  @Override
  public Map<String, MergeFailureReason> getFailingPaths() {
    return this.failingPaths.size() == 0 ? null : this.failingPaths;
  }

  /**
   * Returns whether this merge failed (i.e. not stopped because of a
   * conflict)
   *
   * @return <code>true</code> if a failure occurred, <code>false</code>
   *         otherwise
   */
  @Override
  public boolean failed() {
    return this.failingPaths.size() > 0;
  }

  /**
   * Sets the DirCache which shall be used by this merger. If the DirCache is
   * not set explicitly and if this merger doesn't work in-core, this merger
   * will implicitly get and lock a default DirCache. If the DirCache is
   * explicitly set the caller is responsible to lock it in advance. Finally
   * the merger will call {@link DirCache#commit()} which requires that the
   * DirCache is locked. If the {@link #mergeImpl()} returns without throwing
   * an exception the lock will be released. In case of exceptions the caller
   * is responsible to release the lock.
   *
   * @param dc
   *          the DirCache to set
   */
  @Override
  public void setDirCache(DirCache dc) {
    this.dircache = dc;
    this.implicitDirCache = false;
  }

  /**
   * Sets the WorkingTreeIterator to be used by this merger. If no
   * WorkingTreeIterator is set this merger will ignore the working tree and
   * fail if a content merge is necessary.
   * <p>
   * TODO: enhance WorkingTreeIterator to support write operations. Then this
   * merger will be able to merge with a different working tree abstraction.
   *
   * @param workingTreeIterator
   *          the workingTreeIt to set
   */
  @Override
  public void setWorkingTreeIterator(WorkingTreeIterator workingTreeIterator) {
    this.workingTreeIterator = workingTreeIterator;
  }

  /**
   * The resolve conflict way of three way merging
   *
   * @param baseTree
   * @param headTree
   * @param mergeTree
   * @param ignoreConflicts
   *          Controls what to do in case a content-merge is done and a
   *          conflict is detected. The default setting for this should be
   *          <code>false</code>. In this case the working tree file is
   *          filled with new content (containing conflict markers) and the
   *          index is filled with multiple stages containing BASE, OURS and
   *          THEIRS content. Having such non-0 stages is the sign to git
   *          tools that there are still conflicts for that path.
   *          <p>
   *          If <code>true</code> is specified the behavior is different.
   *          In case a conflict is detected the working tree file is again
   *          filled with new content (containing conflict markers). But
   *          also stage 0 of the index is filled with that content. No
   *          other stages are filled. Means: there is no conflict on that
   *          path but the new content (including conflict markers) is
   *          stored as successful merge result. This is needed in the
   *          context of {@link RecursiveMerger} where when determining
   *          merge bases we don't want to deal with content-merge
   *          conflicts.
   * @return whether the trees merged cleanly
   * @throws IOException
   * @since 3.5
   */
  @Override
  protected boolean mergeTrees(AbstractTreeIterator baseTree, RevTree headTree, RevTree mergeTree,
      boolean ignoreConflicts) throws IOException {

    this.builder = this.dircache.builder();
    DirCacheBuildIterator buildIt = new DirCacheBuildIterator(this.builder);

    this.tw = new NameConflictTreeWalk(this.reader);
    this.tw.addTree(baseTree);
    this.tw.addTree(headTree);
    this.tw.addTree(mergeTree);
    this.tw.addTree(buildIt);
    if (this.workingTreeIterator != null) {
      this.tw.addTree(this.workingTreeIterator);
    } else {
      this.tw.setFilter(TreeFilter.ANY_DIFF);
    }

    if (!mergeTreeWalk(this.tw, ignoreConflicts)) {
      return false;
    }

    if (!this.inCore) {
      // No problem found. The only thing left to be done is to
      // checkout all files from "theirs" which have been selected to
      // go into the new index.
      checkout();

      // All content-merges are successfully done. If we can now write the
      // new index we are on quite safe ground. Even if the checkout of
      // files coming from "theirs" fails the user can work around such
      // failures by checking out the index again.
      if (!this.builder.commit()) {
        cleanUp();
        throw new IndexWriteException();
      }
      this.builder = null;

    } else {
      this.builder.finish();
      this.builder = null;
    }

    if (getUnmergedPaths().isEmpty() && !failed()) {
      this.resultTree = this.dircache.writeTree(getObjectInserter());
      return true;
    } else {
      this.resultTree = null;
      return false;
    }
  }

  /**
   * Process the given TreeWalk's entries.
   *
   * @param treeWalk
   *          The walk to iterate over.
   * @param ignoreConflicts
   *          see
   *          {@link UnleashGitMerger#mergeTrees(AbstractTreeIterator, RevTree, RevTree, boolean)}
   * @return Whether the trees merged cleanly.
   * @throws IOException
   * @since 3.5
   */
  @Override
  protected boolean mergeTreeWalk(TreeWalk treeWalk, boolean ignoreConflicts) throws IOException {
    boolean hasWorkingTreeIterator = this.tw.getTreeCount() > T_FILE;
    while (treeWalk.next()) {
      if (!processEntry(treeWalk.getTree(T_BASE, CanonicalTreeParser.class),
          treeWalk.getTree(T_OURS, CanonicalTreeParser.class), treeWalk.getTree(T_THEIRS, CanonicalTreeParser.class),
          treeWalk.getTree(T_INDEX, DirCacheBuildIterator.class),
          hasWorkingTreeIterator ? treeWalk.getTree(T_FILE, WorkingTreeIterator.class) : null, ignoreConflicts)) {
        cleanUp();
        return false;
      }
      if (treeWalk.isSubtree() && this.enterSubtree) {
        treeWalk.enterSubtree();
      }
    }
    return true;
  }
}
