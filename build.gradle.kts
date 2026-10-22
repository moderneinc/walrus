// https://www.caida.org/catalog/software/plotpaths

plugins {
  java
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(fileTree("libs") { include("*.jar") })
  runtimeOnly("org.jogamp.jogl:jogl-all-main:2.3.2")
  runtimeOnly("org.jogamp.gluegen:gluegen-rt-main:2.3.2")
}

tasks.test {
  // The test source set contains a standalone main() utility.
  failOnNoDiscoveredTests = false
}
