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

package batect.execution.model.events

import batect.config.Container
import batect.docker.DownloadOperation
import batect.docker.build.ActiveImageBuildStep
import batect.docker.build.BuildProgress
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.logRepresentationOf
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ImageBuildProgressEventSpec : Spek({
    describe("an 'image build progress' event") {
        val container = Container("the-container", imageSourceDoesNotMatter())
        val progress = BuildProgress(
            setOf(
                ActiveImageBuildStep.NotDownloading(3, "step 4 of 10: RUN the-thing.sh"),
                ActiveImageBuildStep.Downloading(7, "step 8 of 10: FROM postgres:13.0", DownloadOperation.Extracting, 12, 20)
            )
        )

        val event = ImageBuildProgressEvent(container, progress)

        on("attaching it to a log message") {
            it("returns a machine-readable representation of itself") {
                assertThat(
                    logRepresentationOf(event),
                    equivalentTo(
                        """
                        |{
                        |   "type": "${event::class.qualifiedName}",
                        |   "container": "the-container",
                        |   "buildProgress": {
                        |       "activeSteps": [
                        |           {
                        |               "type": "NotDownloading",
                        |               "stepIndex": 3,
                        |               "name": "step 4 of 10: RUN the-thing.sh"
                        |           },
                        |           {
                        |               "type": "Downloading",
                        |               "stepIndex": 7,
                        |               "name": "step 8 of 10: FROM postgres:13.0",
                        |               "operation": "Extracting",
                        |               "completedBytes": 12,
                        |               "totalBytes": 20
                        |           }
                        |       ]
                        |   }
                        |}
                        """.trimMargin()
                    )
                )
            }
        }
    }
})
