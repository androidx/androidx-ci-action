# AndroidX Workflow Post Build Actions

This repository contains custom post-workflow actions for AndroidX.

Even though it is public, it is not intended to be used in repositories
other than [AndroidX/androidx](https://github.com/androidX/androidx).

## Releasing
Each time a tag is pushed for `latest` or `v*`; a distrubtion build is
prepared in CI that will be pushed at `dist-<tagname>` tag.
See `dist.yml` build action for details.

```
git tag -a v0.11 -m "my changes"
git push origin v0.11 # will trigger a release build
```
Updating the dist-latest:
```
git tag -a -f latest -m "my changes"
git push origin latest -f # need to force to override the tag
```


## Updating AndroidX
[AndroidX](https://github.com/androidx/androidx) uses `dist-latest` versions of
this project to run tests. As a result, changes here are not reflected until
a new `latest` release is made. ([ci config file](https://github.com/androidx/androidx/blob/androidx-main/.github/workflows/integration_tests.yml))

AndroidX also runs the action from the main branch to be able to access firebase keys.
That means, even if you send a PR that changes the config file, its changes will not
impact your PR since your changes won't be on the main branch.

If you need to itereate on the changes across two repositories, do the following:

* Create your branch on the androidx/androidx repository (needed to access FTL keys).
* Create a new build file that looks like [this one](https://github.com/androidx/androidx/blob/yigit/update-ci-action/.github/workflows/integration_tests_manual.yml).
  * Update the `target-run-id` with a run id from one of the successful
    [presubmit runs](https://github.com/androidx/androidx/actions/workflows/presubmit.yml). You can find
    the `target-run-id` in the URL when you navigate to the run page.
  * Update the `run_tests` step to reference your branch on the androidx-ci-action. Note that,
    this does not require you to make a release build as we still have an `action.yml` file that will
    compile and run the action.
* Once you are happy with your changes in androidx-ci-action, send a PR in `androidx-ci-action`
  and take a `latest` release after it is merged.
* Discard your branch on androidx/androidx.



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
