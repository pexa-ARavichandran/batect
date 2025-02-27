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

import batect.docker.DockerNetwork
import batect.testutils.logRepresentationOf
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CustomTaskNetworkCheckedEventSpec : Spek({
    describe("a 'custom task network checked' event") {
        val network = DockerNetwork("some-network")
        val event = CustomTaskNetworkCheckedEvent(network)

        on("attaching it to a log message") {
            it("returns a machine-readable representation of itself") {
                assertThat(
                    logRepresentationOf(event),
                    equivalentTo(
                        """
                        |{
                        |   "type": "${event::class.qualifiedName}",
                        |   "network": {"id": "some-network"}
                        |}
                        """.trimMargin()
                    )
                )
            }
        }
    }
})
