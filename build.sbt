import bintray.Keys._

name := "sbt-liquibase"

organization := "com.sungevity.sbt"

sbtPlugin := true

scalaVersion := "2.10.4"

version := "0.1.0"

libraryDependencies ++= Seq(
  "org.liquibase" % "liquibase-core" % "3.4.1",
  "joda-time" % "joda-time" % "2.8.1"
)

bintraySettings

bintrayPublishSettings

bintrayOrganization in bintray := Some("sungevity")

repository in  bintray := "sbt-plugins"

publishMavenStyle := false

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

