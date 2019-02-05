package com.ms.msde.stash.hook.orphanebranchblocker;

import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.hook.repository.*;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.scm.CommandExitHandler;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.utils.process.StringOutputHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class MergeBaseCommandOutputHandler extends StringOutputHandler implements CommandOutputHandler<String> {
}

class MergeCommandExitHandler implements CommandExitHandler {
    boolean wasSuccessful = false;

    @Override
    public void onCancel(@Nonnull String command, int exitCode, @Nullable String stdErr, @Nullable Throwable thrown) {
    }

    @Override
    public void onExit(@Nonnull String command, int exitCode, @Nullable String stdErr, @Nullable Throwable thrown) {
        wasSuccessful = (exitCode == 0);
    }
}

public class PreReceiveBlockNewRootHook implements PreRepositoryHook<RepositoryHookRequest> {

    private GitCommandBuilderFactory gitCmdBuilderFactory;
    private CommitService commitService;

    public PreReceiveBlockNewRootHook(GitCommandBuilderFactory gitCmdBuilderFactory, CommitService commitService) {
        this.gitCmdBuilderFactory = gitCmdBuilderFactory;
        this.commitService = commitService;
    }

    private boolean hasCommonMergeBase(RepositoryHookRequest request, RefChange refChange, String allowedBranch) {
        MergeCommandExitHandler exitHandler = new MergeCommandExitHandler();
        gitCmdBuilderFactory
                .builder(request.getRepository()).mergeBase()
                .between(refChange.getToHash(), allowedBranch)
                .exitHandler(exitHandler)
                .build(new MergeBaseCommandOutputHandler()).call();
        return exitHandler.wasSuccessful;
    }

    private boolean isIgnored(RepositoryHookRequest request, RefChange refChange, PreRepositoryHookContext context) {
        CommitRequest commitRequest = new CommitRequest.Builder(request.getRepository(), refChange.getToHash()).build();
        String ignoreHookKeyword = context.getSettings().getString("ignoreHookKeyword");
        String commitMessage = commitService.getCommit(commitRequest).getMessage();
        return commitMessage != null && ignoreHookKeyword != null && commitMessage.contains(ignoreHookKeyword);
    }

    @Nonnull
    @Override
    public RepositoryHookResult preUpdate(@Nonnull PreRepositoryHookContext context,
                                          @Nonnull RepositoryHookRequest request) {
        List<String> allowedBranches = Arrays.stream(context.getSettings().getString("allowedBranches")
                .split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        for (String allowedBranch : allowedBranches) {
            for (RefChange refChange : request.getRefChanges()) {
                if (!isIgnored(request, refChange, context) && !hasCommonMergeBase(request, refChange, allowedBranch)) {
                    return RepositoryHookResult.rejected(
                            "Push was rejected because you tried to push from orphaned or not allowed branch " + refChange.getRef().getDisplayId(),
                            "Your branch must have common root commit with one of the following branches: " + String.join(", ", allowedBranches));
                }
            }
        }

        return RepositoryHookResult.accepted();
    }
}
