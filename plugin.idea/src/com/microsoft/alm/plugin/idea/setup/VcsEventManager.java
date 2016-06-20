// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.setup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.microsoft.alm.common.utils.ArgumentHelper;
import com.microsoft.alm.plugin.events.ServerEventManager;
import com.microsoft.alm.plugin.idea.utils.EventContextHelper;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The purpose of this class is to listen for IDEA events like project changed or repository changed and
 * notify the ServerEventManager to fire all changed events.
 */
public class VcsEventManager {
    private static final Logger logger = LoggerFactory.getLogger(VcsEventManager.class);


    private ProjectEventListener projectEventListener;

    private static class Holder {
        private static final VcsEventManager INSTANCE = new VcsEventManager();
    }

    public static VcsEventManager getInstance() {
        return Holder.INSTANCE;
    }

    protected VcsEventManager() {
        logger.info("VcsEventManager created");
    }

    public void startListening() {
        if (projectEventListener == null) {
            projectEventListener = new ProjectEventListener();
            ProjectManager.getInstance().addProjectManagerListener(projectEventListener);
        }
    }

    private void triggerServerEvents(final String sender, final Project project, final GitRepository repository) {
        ArgumentHelper.checkNotEmptyString(sender);
        ArgumentHelper.checkNotNull(project, "project");

        Map<String,Object> context = new HashMap<String, Object>();
        EventContextHelper.setSender(context, sender);
        EventContextHelper.setProject(context, project);
        if (repository != null) {
            EventContextHelper.setRepository(context, repository);
        }

        // Fire all events
        ServerEventManager.getInstance().triggerAllEvents(context);
    }

    private static class ProjectEventListener implements ProjectManagerListener {
        @Override
        public void projectOpened(final Project project) {
            VcsEventManager.getInstance().triggerServerEvents(EventContextHelper.SENDER_PROJECT_OPENED, project, null);
            subscribeToRepoChangeEvents(project);
        }

        @Override
        public boolean canCloseProject(final Project project) {
            return true;
        }

        @Override
        public void projectClosed(final Project project) {
            // nothing to do here
        }

        @Override
        public void projectClosing(final Project project) {
            VcsEventManager.getInstance().triggerServerEvents(EventContextHelper.SENDER_PROJECT_CLOSING, project, null);
        }

        private void subscribeToRepoChangeEvents(@NotNull final Project project) {
            project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
                @Override
                public void repositoryChanged(@NotNull final GitRepository repository) {
                    logger.info("repository changed");
                    VcsEventManager.getInstance().triggerServerEvents(EventContextHelper.SENDER_REPO_CHANGED, project, repository);
                }
            });
        }
    }
}
