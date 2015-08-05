import sbt.complete.DefaultParsers._

name := "sbt-liquibase-example"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "mysql"         % "mysql-connector-java" % "5.1.24"
)

databases in sbtLiquibase := Map(
  "local" -> mysql(env("DB_URL"), env("DB_USERNAME"), env("DB_PASSWORD")).withChangelog("changelog.xml"),
  "remote" -> mysql(env("DB_URL"), env("DB_USERNAME"), env("DB_PASSWORD")).withChangelog("changelog.xml")
)

def env(key: String) = sys.env.get(key).getOrElse(throw new IllegalStateException(s"Please set $key environment variable."))