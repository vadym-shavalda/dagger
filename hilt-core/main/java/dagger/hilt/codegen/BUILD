# Copyright (C) 2020 The Dagger Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Description:
#   This package contains sources used within code generated sources.

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "originating_element",
    srcs = ["OriginatingElement.java"],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/originatingelement:processor",
    ],
    deps = [
        ":package_info",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
