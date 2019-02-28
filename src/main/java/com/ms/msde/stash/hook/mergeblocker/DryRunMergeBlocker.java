package com.ms.msde.stash.hook.mergeblocker;

import com.atlassian.bitbucket.hook.repository.*;

import javax.annotation.Nonnull;

public class DryRunMergeBlocker implements RepositoryMergeCheck {

    /**
     * We are blocking merge dry runs to disable merging of PRs using Merge button
     * (which always do dry run to check if it should be enabled).
     * This mechanism do not block merges using Stash rest API.
     */
    @Nonnull
    @Override
    public RepositoryHookResult preUpdate(@Nonnull PreRepositoryHookContext context,
                                          @Nonnull PullRequestMergeHookRequest request) {
        return request.isDryRun()
                ? RepositoryHookResult.rejected(
                        "Merge from the pull-request is disabled. 'Click for details'",
                "Please use MasterStagingDashboard to manage pull-request life cycle, including merging.")
                :RepositoryHookResult.accepted();
    }
}