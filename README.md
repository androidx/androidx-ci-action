# AndroidX Workflow Post Build Actions

This repository contains custom post-workflow actions for AndroidX.

Even though it is public, it is not intended to be used in repositories
other than [AndroidX/androidx](https://github.com/androidX/androidx).

## Releasing
Each time a tag is pushed for `latest` or `v*`; a distrubtion build is
prepared in CI that will be pushed at `dist-<tagname>` tag.
See `dist.yml` build action for details.

Copyright:

    Copyright 2020 Google LLC

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.