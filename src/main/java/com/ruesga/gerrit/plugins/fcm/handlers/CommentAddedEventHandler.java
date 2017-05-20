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

import org.apache.commons.lang.StringUtils;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.ruesga.gerrit.plugins.fcm.messaging.Notification;
import com.ruesga.gerrit.plugins.fcm.rest.CloudNotificationEvents;
import com.ruesga.gerrit.plugins.fcm.workers.FcmUploaderWorker;

public class CommentAddedEventHandler extends EventHandler
        implements CommentAddedListener {

    @Inject
    public CommentAddedEventHandler(
            @PluginName String pluginName,
            FcmUploaderWorker uploader,
            AllProjectsName allProjectsName,
            ChangeQueryBuilder cqb,
            ChangeQueryProcessor cqp,
            Provider<InternalAccountQuery> accountQueryProvider,
            CapabilityControl.Factory capabilityControlFactory,
            GenericFactory identifiedUserFactory) {
        super(pluginName,
                uploader,
                allProjectsName,
                cqb, cqp,
                accountQueryProvider,
                capabilityControlFactory,
                identifiedUserFactory);
    }

    protected int getEventType() {
        return CloudNotificationEvents.COMMENT_ADDED_EVENT;
    }

    protected NotifyType getNotifyType() {
        return NotifyType.ALL_COMMENTS;
    }

    @Override
    public void onCommentAdded(Event event) {
        Notification notification = createNotification(event);
        if (event.getComment() != null) {
            notification.extra =
                    StringUtils.abbreviate(event.getComment(), 250);
        }
        notification.body = formatAccount(event.getWho())
                + " commented on this change";

        notify(notification, event);
    }

}
