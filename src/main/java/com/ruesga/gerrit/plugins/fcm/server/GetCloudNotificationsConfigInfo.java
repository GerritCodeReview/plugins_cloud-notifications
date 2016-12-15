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

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigResource;
import com.ruesga.gerrit.plugins.fcm.Configuration;
import com.ruesga.gerrit.plugins.fcm.rest.CloudNotificationsConfigInfo;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetCloudNotificationsConfigInfo
        implements RestReadView<ConfigResource> {

    private final Configuration config;

    @Inject
    public GetCloudNotificationsConfigInfo(Configuration config) {
        super();
        this.config = config;
    }

    @Override
    public CloudNotificationsConfigInfo apply(ConfigResource rsrc)
            throws ResourceNotFoundException {
        // Check if plugin is configured
        if (!config.isEnabled()) {
            throw new ResourceNotFoundException("not configured!");
        }

        CloudNotificationsConfigInfo info = new CloudNotificationsConfigInfo();
        info.senderId = this.config.serverSenderId;
        return info;
    }
}
