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

import java.util.Collection;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.events.VoteDeletedListener;
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

public class VoteDeletedEventHandler extends EventHandler
        implements VoteDeletedListener {

    private static class VoteDeletedEntryInfo {
        @SerializedName("vote") public String vote;
        @SerializedName("account") public String account;
    }

    private static class VoteDeletedInfo {
        @SerializedName("votes") public VoteDeletedEntryInfo[] votes;
    }

    @Inject
    public VoteDeletedEventHandler(
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
        return CloudNotificationEvents.VOTE_DELETED_EVENT;
    }

    protected NotifyType getNotifyType() {
        return NotifyType.ALL_COMMENTS;
    }

    @Override
    public void onVoteDeleted(Event event) {
        VoteDeletedInfo info = toVoteDeletedInfo(event);
        final String msg;
        if (info.votes.length == 1) {
            msg = " remove " + info.votes[0].vote + " by " + info.votes[0].account;
        } else {
            msg = " remove multiple votes";
        }
        Notification notification = createNotification(event);
        notification.extra = getSerializer().toJson(info);
        notification.body = formatAccount(event.getWho()) + msg;

        notify(notification, event);
    }

    private VoteDeletedInfo toVoteDeletedInfo(Event event) {
        VoteDeletedInfo info = new VoteDeletedInfo();
        Collection<ApprovalInfo> approvals = event.getOldApprovals().values();
        int count = approvals.size();
        info.votes = new VoteDeletedEntryInfo[count];
        for (int i = 0; i < count; i++)
        for (ApprovalInfo approval : event.getOldApprovals().values()) {
            info.votes[i] = new VoteDeletedEntryInfo();
            info.votes[i].vote = approval.tag + + approval.value;
            info.votes[i].account = formatAccount(approval);
        }
        return info;
    }
}
