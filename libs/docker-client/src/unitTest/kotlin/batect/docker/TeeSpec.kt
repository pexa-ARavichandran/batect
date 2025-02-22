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

package batect.docker

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import okio.Buffer
import okio.sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream

object TeeSpec : Spek({
    describe("a tee sink") {
        val output1 by createForEachTest { ByteArrayOutputStream() }
        val output2 by createForEachTest { ByteArrayOutputStream() }
        val sink by createForEachTest { Tee(output1.sink(), output2.sink()) }

        describe("when a single write is made") {
            beforeEachTest {
                val buffer = Buffer()
                buffer.writeString("hello", Charsets.UTF_8)
                sink.write(buffer, "hello".toByteArray(Charsets.UTF_8).size.toLong())
            }

            it("writes the data to all destinations") {
                assertThat(output1.toString(), equalTo("hello"))
                assertThat(output2.toString(), equalTo("hello"))
            }
        }

        describe("when multiple writes are made") {
            beforeEachTest {
                val buffer = Buffer()
                buffer.writeString("hello", Charsets.UTF_8)
                sink.write(buffer, "hello".toByteArray(Charsets.UTF_8).size.toLong())

                buffer.writeString(" another hello", Charsets.UTF_8)
                sink.write(buffer, " another hello".toByteArray(Charsets.UTF_8).size.toLong())
            }

            it("writes the data to all destinations") {
                assertThat(output1.toString(), equalTo("hello another hello"))
                assertThat(output2.toString(), equalTo("hello another hello"))
            }
        }
    }
})
