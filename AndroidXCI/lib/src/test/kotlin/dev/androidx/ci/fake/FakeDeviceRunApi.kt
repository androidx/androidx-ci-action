/*
 * Copyright 2024 The Android Open Source Project
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

package dev.androidx.ci.fake

import dev.androidx.ci.devicerun.DeviceRunApi
import dev.androidx.ci.generated.ddp.CancelSessionRequest
import dev.androidx.ci.generated.ddp.CancelSessionResponse
import dev.androidx.ci.generated.ddp.CatalogListDevicesResponse
import dev.androidx.ci.generated.ddp.CatalogListSoftwareVersionsResponse
import dev.androidx.ci.generated.ddp.GoogleLongrunningOperation
import dev.androidx.ci.generated.ddp.Session
import java.util.UUID

internal class FakeDeviceRunApi : DeviceRunApi {
    private val sessions = mutableMapOf<String, Session>()
    private var devicesResponse: CatalogListDevicesResponse = CatalogListDevicesResponse()

    override suspend fun createSession(
        projectId: String,
        sessionId: String?,
        session: Session
    ): GoogleLongrunningOperation {
        val id = sessionId ?: UUID.randomUUID().toString()
        sessions[id] = session.copy(
            name = "projects/$projectId/locations/global/sessions/$id"
        )
        return GoogleLongrunningOperation(name = "operations/$id")
    }

    override suspend fun getSession(projectId: String, sessionId: String): Session {
        return sessions[sessionId] ?: throwNotFound<Session>()
    }

    override suspend fun cancelSession(
        projectId: String,
        sessionId: String,
        request: CancelSessionRequest
    ): CancelSessionResponse {
        return CancelSessionResponse()
    }

    override suspend fun listDevices(projectId: String): CatalogListDevicesResponse {
        return devicesResponse
    }

    override suspend fun listSoftwareVersions(projectId: String): CatalogListSoftwareVersionsResponse {
        return CatalogListSoftwareVersionsResponse()
    }

    fun setDevicesResponse(response: CatalogListDevicesResponse) {
        devicesResponse = response
    }

    fun getSessions(): List<Session> = sessions.values.toList()
}
