import bintray.Keys._

name := "sbt-liquibase"

organization := "com.github.vorlov"

sbtPlugin := true

scalaVersion := "2.10.4"

version := "0.1.0"

libraryDependencies ++= Seq(
  "org.liquibase" % "liquibase-core" % "3.3.5"
)

bintraySettings

bintrayPublishSettings

bintrayOrganization in bintray := Some("vorlov")

repository in  bintray := "sbt-plugins"

publishMavenStyle := false

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))
