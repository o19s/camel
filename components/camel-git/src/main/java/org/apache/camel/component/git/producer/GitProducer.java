/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.git.producer;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.git.GitConstants;
import org.apache.camel.component.git.GitEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GitProducer.class);

    private final GitEndpoint endpoint;

    private Repository repo;

    private Git git;

    public GitProducer(GitEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.repo = getLocalRepository();
        this.git = new Git(repo);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        repo.close();
        git.close();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation;
        if (ObjectHelper.isEmpty(endpoint.getOperation())) {
            operation = exchange.getIn().getHeader(GitConstants.GIT_OPERATION, String.class);
        } else {
            operation = endpoint.getOperation();
        }
        if (ObjectHelper.isEmpty(endpoint.getLocalPath())) {
            throw new IllegalArgumentException("Local path must specified to execute " + operation);
        }

        switch (operation) {
        
        case GitOperation.CLONE_OPERATION:
            doClone(exchange, operation);
            break;

        case GitOperation.INIT_OPERATION:
            doInit(exchange, operation);
            break;

        case GitOperation.ADD_OPERATION:
            doAdd(exchange, operation);
            break;

        case GitOperation.REMOVE_OPERATION:
            doRemove(exchange, operation);
            break;

        case GitOperation.COMMIT_OPERATION:
            doCommit(exchange, operation);
            break;

        case GitOperation.COMMIT_ALL_OPERATION:
            doCommitAll(exchange, operation);
            break;

        case GitOperation.CREATE_BRANCH_OPERATION:
            doCreateBranch(exchange, operation);
            break;

        case GitOperation.DELETE_BRANCH_OPERATION:
            doDeleteBranch(exchange, operation);
            break;

        case GitOperation.STATUS_OPERATION:
            doStatus(exchange, operation);
            break;

        case GitOperation.LOG_OPERATION:
            doLog(exchange, operation);
            break;

        case GitOperation.PUSH_OPERATION:
            doPush(exchange, operation);
            break;

        case GitOperation.PULL_OPERATION:
            doPull(exchange, operation);
            break;

        case GitOperation.CREATE_TAG_OPERATION:
            doCreateTag(exchange, operation);
            break;

        case GitOperation.DELETE_TAG_OPERATION:
            doDeleteTag(exchange, operation);
            break;
            
        case GitOperation.SHOW_BRANCHES:
            doShowBranches(exchange, operation);
            break;
                
        default:
            throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doClone(Exchange exchange, String operation) throws Exception {
        Git result = null;
        if (ObjectHelper.isEmpty(endpoint.getLocalPath())) {
            throw new IllegalArgumentException("Local path must specified to execute " + operation);
        }
        try {
            File localRepo = new File(endpoint.getLocalPath(), "");
            if (!localRepo.exists()) {
                if (ObjectHelper.isNotEmpty(endpoint.getUsername()) && ObjectHelper.isNotEmpty(endpoint.getPassword())) {
                    UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider(endpoint.getUsername(), endpoint.getPassword());
                    result = git.cloneRepository().setCredentialsProvider(credentials).setURI(endpoint.getRemotePath()).setDirectory(new File(endpoint.getLocalPath(), "")).call();
                } else {
                    result = git.cloneRepository().setURI(endpoint.getRemotePath()).setDirectory(new File(endpoint.getLocalPath(), "")).call();
                }
            } else {
                throw new IllegalArgumentException("The local repository directory already exists");
            }
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        } finally {
            result.close();
        }
    }

    protected void doInit(Exchange exchange, String operation) throws Exception {
        Git result = null;
        if (ObjectHelper.isEmpty(endpoint.getLocalPath())) {
            throw new IllegalArgumentException("Local path must specified to execute " + operation);
        }
        try {
            result = git.init().setDirectory(new File(endpoint.getLocalPath(), "")).setBare(false).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        } finally {
            result.close();
        }
    }

    protected void doAdd(Exchange exchange, String operation) throws Exception {
        String fileName = null;
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME))) {
            fileName = exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME, String.class);
        } else {
            throw new IllegalArgumentException("File name must be specified to execute " + operation);
        }
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            git.add().addFilepattern(fileName).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
    }

    protected void doRemove(Exchange exchange, String operation) throws Exception {
        String fileName = null;
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME))) {
            fileName = exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME, String.class);
        } else {
            throw new IllegalArgumentException("File name must be specified to execute " + operation);
        }
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            git.rm().addFilepattern(fileName).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
    }

    protected void doCommit(Exchange exchange, String operation) throws Exception {
        String commitMessage = null;
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_COMMIT_MESSAGE))) {
            commitMessage = exchange.getIn().getHeader(GitConstants.GIT_COMMIT_MESSAGE, String.class);
        } else {
            throw new IllegalArgumentException("Commit message must be specified to execute " + operation);
        }
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            git.commit().setMessage(commitMessage).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
    }

    protected void doCommitAll(Exchange exchange, String operation) throws Exception {
        String commitMessage = null;
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_COMMIT_MESSAGE))) {
            commitMessage = exchange.getIn().getHeader(GitConstants.GIT_COMMIT_MESSAGE, String.class);
        } else {
            throw new IllegalArgumentException("Commit message must be specified to execute " + operation);
        }
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            git.commit().setAll(true).setMessage(commitMessage).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
    }

    protected void doCreateBranch(Exchange exchange, String operation) throws Exception {
        if (ObjectHelper.isEmpty(endpoint.getBranchName())) {
            throw new IllegalArgumentException("Branch Name must be specified to execute " + operation);
        }
        try {
            git.branchCreate().setName(endpoint.getBranchName()).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
    }

    protected void doDeleteBranch(Exchange exchange, String operation) throws Exception {
        if (ObjectHelper.isEmpty(endpoint.getBranchName())) {
            throw new IllegalArgumentException("Branch Name must be specified to execute " + operation);
        }
        try {
            git.branchDelete().setBranchNames(endpoint.getBranchName()).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
    }

    protected void doStatus(Exchange exchange, String operation) throws Exception {
        Status status = null;
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            status = git.status().call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
        exchange.getOut().setBody(status);
    }

    protected void doLog(Exchange exchange, String operation) throws Exception {
        Iterable<RevCommit> revCommit = null;
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            revCommit = git.log().call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
        exchange.getOut().setBody(revCommit);
    }

    protected void doPush(Exchange exchange, String operation) throws Exception {
        Iterable<PushResult> result = null;
        try {
            if (ObjectHelper.isEmpty(endpoint.getRemotePath())) {
                throw new IllegalArgumentException("Remote path must be specified to execute " + operation);
            }
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            if (ObjectHelper.isNotEmpty(endpoint.getUsername()) && ObjectHelper.isNotEmpty(endpoint.getPassword())) {
                UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider(endpoint.getUsername(), endpoint.getPassword());
                result = git.push().setCredentialsProvider(credentials).setRemote(endpoint.getRemotePath()).call();
            } else {
                result = git.push().setRemote(endpoint.getRemotePath()).call();
            }
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
        exchange.getOut().setBody(result);
    }

    protected void doPull(Exchange exchange, String operation) throws Exception {
        PullResult result = null;
        try {
            if (ObjectHelper.isEmpty(endpoint.getRemotePath())) {
                throw new IllegalArgumentException("Remote path must be specified to execute " + operation);
            }
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            if (ObjectHelper.isNotEmpty(endpoint.getUsername()) && ObjectHelper.isNotEmpty(endpoint.getPassword())) {
                UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider(endpoint.getUsername(), endpoint.getPassword());
                result = git.pull().setCredentialsProvider(credentials).setRemote(endpoint.getRemotePath()).call();
            } else {
                result = git.pull().setRemote(endpoint.getRemotePath()).call();
            }
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
        exchange.getOut().setBody(result);
    }

    protected void doCreateTag(Exchange exchange, String operation) throws Exception {
        if (ObjectHelper.isEmpty(endpoint.getTagName())) {
            throw new IllegalArgumentException("Tag Name must be specified to execute " + operation);
        }
        try {
            git.tag().setName(endpoint.getTagName()).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
    }

    protected void doDeleteTag(Exchange exchange, String operation) throws Exception {
        if (ObjectHelper.isEmpty(endpoint.getTagName())) {
            throw new IllegalArgumentException("Tag Name must be specified to execute " + operation);
        }
        try {
            git.tagDelete().setTags(endpoint.getTagName()).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
    }
    
    protected void doShowBranches(Exchange exchange, String operation) throws Exception {
        List<Ref> result = null;
        try {
            result = git.branchList().setListMode(ListMode.ALL).call();
        } catch (Exception e) {
            LOG.error("There was an error in Git " + operation + " operation");
            throw e;
        }
        exchange.getOut().setBody(result);
    }

    private Repository getLocalRepository() throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repo = null;
        try {
            repo = builder.setGitDir(new File(endpoint.getLocalPath(), ".git")).readEnvironment() // scan environment GIT_* variables
                    .findGitDir() // scan up the file system tree
                    .build();
        } catch (IOException e) {
            LOG.error("There was an error, cannot open " + endpoint.getLocalPath() + " repository");
            throw e;
        }
        return repo;
    }
}
