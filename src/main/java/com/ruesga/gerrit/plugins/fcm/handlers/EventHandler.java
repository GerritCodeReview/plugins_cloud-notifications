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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.ChangeEvent;
import com.google.gerrit.extensions.events.RevisionEvent;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.server.group.Groups;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.query.change.SingleGroupUser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import com.ruesga.gerrit.plugins.fcm.messaging.Notification;
import com.ruesga.gerrit.plugins.fcm.workers.FcmUploaderWorker;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class EventHandler {

    private static final Logger log =
            LoggerFactory.getLogger(EventHandler.class);

    private final String pluginName;
    private final FcmUploaderWorker uploader;
    private final AllProjectsName allProjectsName;
    private final ChangeQueryBuilder cqb;
    private final ChangeQueryProcessor cqp;
    private final ProjectCache projectCache;
    private final GroupBackend groupBackend;
    private final GroupIncludeCache groupIncludes;
    private final Provider<ReviewDb> db;
    private final Provider<InternalAccountQuery> accountQueryProvider;
    private final GenericFactory identifiedUserFactory;
    private final Provider<AnonymousUser> anonymousProvider;
    private final Groups groups;
    private final Gson gson;

    public EventHandler(
        @PluginName String pluginName,
        FcmUploaderWorker uploader,
        AllProjectsName allProjectsName,
        ChangeQueryBuilder cqb,
        ChangeQueryProcessor cqp,
        ProjectCache projectCache,
        GroupIncludeCache groupIncludes,
        GroupBackend groupBackend,
        Provider<ReviewDb> db,
        Provider<InternalAccountQuery> accountQueryProvider,
        GenericFactory identifiedUserFactory,
        Provider<AnonymousUser> anonymousProvider,
        Groups groups) {
        super();
        this.pluginName = pluginName;
        this.uploader = uploader;
        this.allProjectsName = allProjectsName;
        this.cqb = cqb;
        this.cqp = cqp;
        this.projectCache = projectCache;
        this.groupIncludes = groupIncludes;
        this.groupBackend = groupBackend;
        this.db = db;
        this.accountQueryProvider = accountQueryProvider;
        this.identifiedUserFactory = identifiedUserFactory;
        this.anonymousProvider = anonymousProvider;
        this.groups = groups;
        this.gson = new GsonBuilder().create();
    }

    protected abstract int getEventType();

    protected abstract NotifyType getNotifyType();

    Gson getSerializer() {
        return this.gson;
    }

    protected Notification createNotification(ChangeEvent event) {
        Notification notification = new Notification();
        notification.event = getEventType();
        notification.when = event.getWhen().getTime() / 1000L;
        notification.who = event.getWho();
        notification.change = event.getChange().changeId;
        notification.legacyChangeId = event.getChange()._number;
        notification.project = event.getChange().project;
        notification.branch = event.getChange().branch;
        notification.topic = event.getChange().topic;
        notification.subject = StringUtils.abbreviate(
                event.getChange().subject, 100);
        if (event instanceof RevisionEvent) {
            notification.revision =
                    ((RevisionEvent) event).getRevision().commit.commit;
        }
        return notification;
    }

    protected void notify(Notification notification, ChangeEvent event) {
        // Check if this event should be notified
        if (event.getNotify().equals(NotifyHandling.NONE)) {
            if (log.isDebugEnabled()) {
                log.debug(
                    String.format("[%s] Notify event %d is not enabled: %s",
                        pluginName, getEventType(), gson.toJson(notification)));
            }
            return;
        }

        // Obtain information about the accounts that need to be
        // notified related to this event
        List<Integer> notifiedUsers = obtainNotifiedAccounts(event);
        if (notifiedUsers.isEmpty()) {
            // Nobody to notify about this event
            return;
        }

        // Perform notification
        if (log.isDebugEnabled()) {
            log.debug(String.format("[%s] Sending notification %s to %s",
                    pluginName, gson.toJson(notification),
                    gson.toJson(notifiedUsers)));
        }
        this.uploader.notifyTo(notifiedUsers, notification);
    }

    private List<Integer> obtainNotifiedAccounts(ChangeEvent event) {
        Set<Integer> notifiedUsers = new HashSet<>();
        ChangeInfo change = event.getChange();
        NotifyHandling notifyTo = event.getNotify();

        // 1.- Owner of the change
        notifiedUsers.add(change.owner._accountId);

        // 2.- Reviewers
        if (notifyTo.equals(NotifyHandling.OWNER_REVIEWERS) ||
                notifyTo.equals(NotifyHandling.ALL)) {
            if (change.reviewers != null) {
                for (ReviewerState state : change.reviewers.keySet()) {
                    Collection<AccountInfo> accounts =
                            change.reviewers.get(state);
                    for (AccountInfo account : accounts) {
                        notifiedUsers.add(account._accountId);
                    }
                }
            }
        }

        // 3.- Watchers
        ChangeData changeData = obtainChangeData(change);
        if (changeData != null) {
            notifiedUsers.addAll(getWatchers(getNotifyType(), changeData,
                    !safeBoolean(change.workInProgress) && !safeBoolean(change.isPrivate)));
        }

        // 4.- Remove the author of this event (he doesn't need to get
        // the notification)
        notifiedUsers.remove(event.getWho()._accountId);

        return new ArrayList<>(notifiedUsers);
    }

    private Set<Integer> getWatchers(NotifyType type, ChangeData change, boolean includeWatchersFromNotifyConfig) {
        Set<Integer> watchers = new HashSet<>();
        try {
            Set<Account.Id> projectWatchers = new HashSet<>();
            for (AccountState a : accountQueryProvider.get().byWatchedProject(
                    change.project())) {
                Account.Id accountId = a.getAccount().getId();
                for (Map.Entry<ProjectWatchKey, Set<NotifyType>> e : a.getProjectWatches().entrySet()) {
                    if (change.project().equals(e.getKey().project())
                            && add(watchers, accountId, e.getKey(), e.getValue(), type, change)) {
                        // We only want to prevent matching All-Projects if this filter hits
                        projectWatchers.add(accountId);
                    }
                }
            }

            for (AccountState a : accountQueryProvider.get().byWatchedProject(allProjectsName)) {
              for (Map.Entry<ProjectWatchKey, Set<NotifyType>> e : a.getProjectWatches().entrySet()) {
                    if (allProjectsName.equals(e.getKey().project())) {
                        Account.Id accountId = a.getAccount().getId();
                        if (!projectWatchers.contains(accountId)) {
                            add(watchers, accountId, e.getKey(), e.getValue(), type, change);
                        }
                    }
                }
            }

            if (includeWatchersFromNotifyConfig) {
                ProjectState projectState = projectCache.get(change.project());
                if (projectState != null) {
                    for (ProjectState state : projectState.tree()) {
                        for (NotifyConfig nc : state.getConfig().getNotifyConfigs()) {
                            if (nc.isNotify(type)) {
                                try {
                                    add(watchers, nc, change);
                                } catch (QueryParseException e) {
                                    log.warn(
                                        "Project {} has invalid notify {} filter \"{}\": {}",
                                        state.getName(),
                                        nc.getName(),
                                        nc.getFilter(),
                                        e.getMessage());
                                }
                            }
                        }
                    }
                }
            }

        } catch (OrmException ex) {
            log.error(String.format(
                    "[%s] Failed to obtain watchers", pluginName), ex);
        }
        return watchers;
    }

    private boolean add(Set<Integer> watchers, Account.Id accountId,
            ProjectWatchKey key, Set<NotifyType> watchedTypes, NotifyType type,
            ChangeData change) throws OrmException {
        IdentifiedUser user = identifiedUserFactory.create(accountId);

        try {
            if (filterMatch(user, key.filter(), change)) {
                // If we are set to notify on this type, add the user.
                // Otherwise, still return true to stop notifications for this user.
                if (watchedTypes.contains(type)) {
                    watchers.add(accountId.get());
                }
                return true;
            }
        } catch (QueryParseException e) {
            // Ignore broken filter expressions.
        }
        return false;
    }

    private void add(Set<Integer> watchers, NotifyConfig nc, ChangeData change)
            throws OrmException, QueryParseException {
        for (GroupReference ref : nc.getGroups()) {
            CurrentUser user = new SingleGroupUser(ref.getUUID());
            if (filterMatch(user, nc.getFilter(), change)) {
                deliverToMembers(watchers, ref.getUUID());
            }
        }
    }

    private boolean filterMatch(
            CurrentUser user, String filter, ChangeData change)
            throws OrmException, QueryParseException {
        ChangeQueryBuilder qb;
        Predicate<ChangeData> p = null;
        if (user == null) {
            qb = cqb.asUser(anonymousProvider.get());
        } else {
            qb = cqb.asUser(user);
            p = qb.is_visible();
        }

        if (filter != null) {
            Predicate<ChangeData> filterPredicate = qb.parse(filter);
            if (p == null) {
                p = filterPredicate;
            } else {
                p = Predicate.and(filterPredicate, p);
            }
        }
        return p == null || p.asMatchable().match(change);
    }

    private ChangeData obtainChangeData(ChangeInfo change) {
        try {
            QueryResult<ChangeData> changeQuery =
                    cqp.query(cqb.parse("change:" + change._number));
            List<ChangeData> changeQueryResults = changeQuery.entities();
            if (changeQueryResults == null || changeQueryResults.isEmpty()) {
                log.warn(String.format("[%s] No change found for %s",
                        pluginName, change._number));
                return null;
            }
            return changeQueryResults.get(0);

        } catch (Exception ex) {
            log.error(String.format("[%s] Failed to obtain change data: %d",
                    pluginName, change._number), ex);
        }
        return null;
    }

    private void deliverToMembers(Set<Integer> watchers, AccountGroup.UUID startUUID)
            throws OrmException {
        ReviewDb db = this.db.get();
        Set<AccountGroup.UUID> seen = new HashSet<>();
        List<AccountGroup.UUID> q = new ArrayList<>();

        seen.add(startUUID);
        q.add(startUUID);

        while (!q.isEmpty()) {
            AccountGroup.UUID uuid = q.remove(q.size() - 1);
            GroupDescription.Basic group = groupBackend.get(uuid);
            if (group == null) {
              continue;
            }

            if (!(group instanceof GroupDescription.Internal)) {
              // Non-internal groups cannot be expanded by the server.
              continue;
            }

            GroupDescription.Internal ig = (GroupDescription.Internal) group;
            try {
                groups.getMembers(db, ig.getGroupUUID()).forEach(g -> watchers.add(g.get()));
            } catch (NoSuchGroupException e) {
                continue;
            }
            for (AccountGroup.UUID m : groupIncludes.subgroupsOf(uuid)) {
                if (seen.add(m)) {
                  q.add(m);
                }
            }
        }
    }

    protected String formatAccount(AccountInfo account) {
        if (account.name != null) {
            return account.name;
        }
        if (account.username != null) {
            return account.username;
        }
        return account.email;
    }

    boolean safeBoolean(Boolean value) {
        return value != null && value;
    }
}
