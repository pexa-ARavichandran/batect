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

val okhttpVersion: String by project
val jnrUnixsocketVersion: String by project

plugins {
    id("batect-kotlin")
}

dependencies {
    implementation(platform("com.squareup.okhttp3:okhttp-bom:$okhttpVersion"))

    implementation("com.github.jnr:jnr-unixsocket:$jnrUnixsocketVersion")
    implementation("com.squareup.okhttp3:okhttp")

    implementation(project(":libs:os"))

    testImplementation(project(":libs:test-utils"))
}

checkUnitTestLayout {
    ignoreFileNameCheck.set(
        fileTree("src/unitTest/kotlin") {
            include("batect/sockets/namedpipes/NamedPipeTestServer.kt")
        }
    )
}
