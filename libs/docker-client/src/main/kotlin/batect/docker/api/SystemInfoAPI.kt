/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.docker.api

import batect.docker.DockerException
import batect.docker.DockerHttpConfig
import batect.docker.DockerVersionInfo
import batect.docker.Json
import batect.logging.Logger
import batect.os.SystemInfo
import batect.primitives.Version
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

class SystemInfoAPI(
    httpConfig: DockerHttpConfig,
    systemInfo: SystemInfo,
    logger: Logger
) : APIBase(httpConfig, systemInfo, logger) {
    fun getServerVersionInfo(): DockerVersionInfo {
        logger.info {
            message("Getting Docker version information.")
        }

        val url = baseUrl.newBuilder()
            .addPathSegment("version")
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not get Docker version info.")
                    data("error", error)
                }

                throw DockerVersionInfoRetrievalException("The request failed: ${error.message}")
            }

            val parsedResponse = Json.default.parseToJsonElement(response.body!!.string()).jsonObject
            val version = parsedResponse.getValue("Version").jsonPrimitive.content
            val apiVersion = parsedResponse.getValue("ApiVersion").jsonPrimitive.content
            val minAPIVersion = parsedResponse.getValue("MinAPIVersion").jsonPrimitive.content
            val gitCommit = parsedResponse.getValue("GitCommit").jsonPrimitive.content
            val operatingSystem = parsedResponse.getValue("Os").jsonPrimitive.content
            val experimental = parsedResponse["Experimental"]?.jsonPrimitive?.boolean ?: false

            return DockerVersionInfo(
                Version.parse(version),
                apiVersion,
                minAPIVersion,
                gitCommit,
                operatingSystem,
                experimental
            )
        }
    }

    fun ping(): PingResponse {
        logger.info {
            message("Pinging daemon.")
        }

        val url = httpConfig.baseUrl.newBuilder()
            .addPathSegment("_ping")
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not ping daemon.")
                    data("error", error)
                }

                throw DockerException("Could not ping Docker daemon, daemon responded with HTTP ${response.code}: ${error.message}")
            }

            val responseBody = response.body!!.string()

            if (responseBody != "OK") {
                throw DockerException("Could not ping Docker daemon, daemon responded with HTTP ${response.code}: $responseBody")
            }

            return when (val builderVersionHeader = response.headers["Builder-Version"] ?: "1") {
                "", "1" -> PingResponse(BuilderVersion.Legacy)
                "2" -> PingResponse(BuilderVersion.BuildKit)
                else -> throw DockerException("Docker daemon responded with unknown Builder-Version '$builderVersionHeader'.")
            }
        }
    }
}

@Serializable
data class PingResponse(val builderVersion: BuilderVersion)

enum class BuilderVersion {
    Legacy,
    BuildKit
}
