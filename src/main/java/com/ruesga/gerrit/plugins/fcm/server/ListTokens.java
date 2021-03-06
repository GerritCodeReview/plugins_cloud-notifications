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
package com.ruesga.gerrit.plugins.fcm.server;

import java.util.List;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.ruesga.gerrit.plugins.fcm.Configuration;
import com.ruesga.gerrit.plugins.fcm.DatabaseManager;
import com.ruesga.gerrit.plugins.fcm.rest.CloudNotificationInfo;

@Singleton
public class ListTokens implements RestReadView<DeviceResource> {

    private final Provider<CurrentUser> self;
    private final DatabaseManager db;
    private final Configuration config;

    @Inject
    public ListTokens(
            Provider<CurrentUser> self,
            DatabaseManager db,
            Configuration config) {
        super();
        this.self = self;
        this.db = db;
        this.config = config;
    }

    @Override
    public List<CloudNotificationInfo> apply(DeviceResource rsrc)
            throws BadRequestException, ResourceNotFoundException {
        // Check if plugin is configured
        if (!config.isEnabled()) {
            throw new ResourceNotFoundException("not configured!");
        }

        // Request are only valid from the current authenticated user
        if (self.get() == null || self.get() != rsrc.getUser()) {
            throw new BadRequestException("invalid account!");
        }

        // Obtain the list of tokens for the device
        return db.getCloudNotifications(
                self.get().getAccountId().get(), rsrc.getDevice());
    }
}
