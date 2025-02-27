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

package batect.ui.containerio

import batect.testutils.createForEachTest
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.PrintStream

object UncloseableSinkSpec : Spek({
    describe("an uncloseable sink") {
        val destination by createForEachTest { mock<PrintStream>() }
        val sink by createForEachTest { UncloseableSink(destination) }

        describe("closing the sink") {
            beforeEachTest { sink.close() }

            it("does not close the destination") {
                verify(destination, never()).close()
            }
        }
    }
})
