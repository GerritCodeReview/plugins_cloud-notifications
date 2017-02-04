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

gerrit_plugin(
    name = "cloud-notifications",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    manifest_entries = [
        "Gerrit-PluginName: cloud-notifications",
        "Gerrit-Module: com.ruesga.gerrit.plugins.fcm.ApiModule",
        "Implementation-Title: Firebase Cloud Notifications Plugin",
        "Implementation-Vendor: Jorge Ruesga",
        "Implementation-URL: https://gerrit.googlesource.com/plugins/cloud-notifications",
    ],
    deps = [
         "//lib:h2",
    ],
    provided_deps = [
        "//lib:gson",
    ],
)
