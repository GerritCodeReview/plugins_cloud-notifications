/*
 * Copyright (C) 2016 Jorge Ruesga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ruesga.gerrit.plugins.fcm.handlers;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.AssigneeChangedListener;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gson.annotations.SerializedName;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.ruesga.gerrit.plugins.fcm.messaging.Notification;
import com.ruesga.gerrit.plugins.fcm.rest.CloudNotificationEvents;
import com.ruesga.gerrit.plugins.fcm.workers.FcmUploaderWorker;

public class AssigneeChangedEventHandler extends EventHandler
        implements AssigneeChangedListener {

    private static class AssigneeInfo {
        @SerializedName("old") public AccountInfo old;
        @SerializedName("new") public AccountInfo _new;
    }

    @Inject
    public AssigneeChangedEventHandler(
            @PluginName String pluginName,
            FcmUploaderWorker uploader,
            AllProjectsName allProjectsName,
            ChangeQueryBuilder cqb,
            ChangeQueryProcessor cqp,
            ProjectCache projectCache,
            GroupBackend groupBackend,
            Provider<InternalAccountQuery> accountQueryProvider,
            GenericFactory identifiedUserFactory,
            Provider<AnonymousUser> anonymousProvider) {
        super(pluginName,
                uploader,
                allProjectsName,
                cqb, cqp,
                projectCache,
                groupBackend,
                accountQueryProvider,
                identifiedUserFactory,
                anonymousProvider);
    }

    protected int getEventType() {
        return CloudNotificationEvents.ASSIGNEE_CHANGED_EVENT;
    }

    protected NotifyType getNotifyType() {
        return NotifyType.NEW_PATCHSETS;
    }

    @Override
    public void onAssigneeChanged(Event event) {
        AssigneeInfo assignee = new AssigneeInfo();
        assignee.old = event.getOldAssignee();
        assignee._new = event.getChange().assignee;
        Notification notification = createNotification(event);
        notification.extra = getSerializer().toJson(assignee);
        if (assignee._new == null) {
            notification.body = formatAccount(event.getWho())
                    + " unassigned " + formatAccount(assignee.old)
                    + " from this change";
        } else {
            notification.body = formatAccount(event.getWho())
                    + " assigned " + formatAccount(assignee._new)
                    + " to this change";
        }

        notify(notification, event);
    }

}
