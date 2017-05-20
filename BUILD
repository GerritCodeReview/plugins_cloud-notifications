#
# Copyright (C) 2016 Jorge Ruesga
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

load("//tools/bzl:plugin.bzl", "gerrit_plugin")

SOURCES = glob(['src/main/java/**/*.java'])
RESOURCES = glob(['src/main/resources/**/*'])

DEPS = [
  '//lib:h2'
]

gerrit_plugin(
  name = "cloud-notifications",
  srcs = SOURCES,
  resources = RESOURCES,
  manifest_entries = [
    'Gerrit-PluginName: cloud-notifications',
    'Gerrit-ApiVersion: 2.14-SNAPSHOT',
    'Gerrit-Module: com.ruesga.gerrit.plugins.fcm.ApiModule',
    'Implementation-Title: Firebase Cloud Notifications Plugin',
    'Implementation-URL: https://gerrit.googlesource.com/plugins/cloud-notifications',
    'Implementation-Version: 2.14-SNAPSHOT'
  ],
  deps = DEPS
)

