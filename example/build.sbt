import sbt.complete.DefaultParsers._

name := "sbt-liquibase-example"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "mysql"         % "mysql-connector-java" % "5.1.24",
  "com.sungevity.dbschemas" %% "cp" % "1.0+"
)

dbUsername in sbtLiquibase := sys.env.get("DB_USERNAME").getOrElse("")

changeLogFile in sbtLiquibase := "changelog.sql"

dbPassword in sbtLiquibase := "root"

dbURL in sbtLiquibase := "jdbc:mysql://localhost/testl"

dbDriver in sbtLiquibase := "com.mysql.jdbc.Driver"