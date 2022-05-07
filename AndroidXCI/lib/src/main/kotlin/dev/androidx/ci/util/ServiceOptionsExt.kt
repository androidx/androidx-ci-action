/*
 * Copyright 2022 The Android Open Source Project
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

package dev.androidx.ci.util

import com.google.cloud.Service
import com.google.cloud.ServiceOptions
import dev.androidx.ci.config.Config

/**
 * Configures the given builder with [Config.Gcp].
 */
internal fun <ServiceT : Service<OptionsT>,
    OptionsT : ServiceOptions<ServiceT, OptionsT>,
    B : ServiceOptions.Builder<ServiceT, OptionsT, B>>
ServiceOptions.Builder<ServiceT, OptionsT, B>.configure(gcpConfig: Config.Gcp): B {
    return this.setCredentials(gcpConfig.credentials).setProjectId(gcpConfig.projectId)
}
