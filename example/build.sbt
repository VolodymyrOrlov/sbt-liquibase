import sbt.complete.DefaultParsers._

name := "sbt-liquibase-example"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "mysql"         % "mysql-connector-java" % "5.1.24"
)

dbUsername in sbtLiquibase := env("DB_USERNAME")

dbPassword in sbtLiquibase := env("DB_PASSWORD")

dbURL in sbtLiquibase := env("DB_URL")

changeLogFile in sbtLiquibase := env("CHANGELOG")

dbDriver in sbtLiquibase := env("DB_DRIVER")

def env(key: String) = sys.env.get(key).getOrElse(throw new IllegalStateException(s"Please set $key environment variable."))