package com.atlassian.bitbucket.server.examples;

import com.atlassian.bitbucket.commit.Commit;
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
import java.util.Objects;

class MergeBaseCommandOutputHandler extends StringOutputHandler implements CommandOutputHandler<String> {}

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
    private String ignoreHookKeyword = "NEW_ROOT";
    private List<String> allowedBranches = Arrays.asList("master");

    public PreReceiveBlockNewRootHook(GitCommandBuilderFactory gitCmdBuilderFactory, CommitService commitService) {
        this.gitCmdBuilderFactory = gitCmdBuilderFactory;
        this.commitService = commitService;
    }

    @Nonnull
    @Override
    public RepositoryHookResult preUpdate(@Nonnull PreRepositoryHookContext context,
                                          @Nonnull RepositoryHookRequest request) {

        String rootHash = request.getRefChanges().iterator().next().getToHash();
        Commit rootCommit = commitService.getCommit(new CommitRequest.Builder(request.getRepository(), rootHash).build());
        if (Objects.requireNonNull(rootCommit.getMessage()).contains(ignoreHookKeyword)) {
            return RepositoryHookResult.accepted();
        }

        for (String allowedBranch : allowedBranches) {
            for (RefChange refChange : request.getRefChanges()) {
                MergeCommandExitHandler exitHandler = new MergeCommandExitHandler();

                gitCmdBuilderFactory
                        .builder(request.getRepository()).mergeBase()
                        .between(refChange.getToHash(), allowedBranch)
                        .exitHandler(exitHandler)
                        .build(new MergeBaseCommandOutputHandler()).call();

                if (exitHandler.wasSuccessful) return RepositoryHookResult.accepted();
            }
        }

        return RepositoryHookResult.rejected("Push was rejected because no common root commit found was found.",
                "This happened because you tried to push from orphaned or not allowed branch.\n" +
                        "Allowed branches: " + String.join(", ", allowedBranches));
    }
}
