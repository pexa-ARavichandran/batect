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

val kotlinxSerializationVersion: String by project
val jnrPosixVersion: String by project
val osPlatformFinderVersion: String by project
val jimfsVersion: String by project
val hamkrestJsonVersion: String by project

plugins {
    id("batect-kotlin")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("com.github.jnr:jnr-posix:$jnrPosixVersion")
    implementation("org.graylog.repackaged:os-platform-finder:$osPlatformFinderVersion")

    implementation(project(":libs:logging"))
    implementation(project(":libs:primitives"))

    testImplementation("com.google.jimfs:jimfs:$jimfsVersion")
    testImplementation("org.araqnid.hamkrest:hamkrest-json:$hamkrestJsonVersion")
    testImplementation(project(":libs:test-utils"))
    testImplementation(project(":libs:logging-test-utils"))
}
