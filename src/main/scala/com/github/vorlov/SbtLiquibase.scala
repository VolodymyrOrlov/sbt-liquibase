package com.github.vorlov

import java.io.{File, OutputStreamWriter}
import java.nio.file.{Paths, Path, StandardCopyOption, Files}

import liquibase.Liquibase
import liquibase.database.Database
import liquibase.integration.commandline.CommandLineUtils
import liquibase.resource.{ClassLoaderResourceAccessor, FileSystemResourceAccessor}
import sbt.KeyRanks._
import sbt.Keys._
import sbt._
import sbt.classpath.ClasspathUtilities

object SbtLiquibase extends AutoPlugin {

  class RichLiquibase(liquibase: Liquibase) {
    def execAndClose(f: (Liquibase) => Unit): Unit = {
      try { f(liquibase) } finally { liquibase.getDatabase.close() }
    }
  }
  implicit def RichLiquibase(liquibase: Liquibase): RichLiquibase = new RichLiquibase(liquibase)

  object autoImport {

    val update = TaskKey[Unit]("up", "Run a liquibase migration")

    val sbtLiquibase = TaskKey[String]("sbt-liquibase", "sbt-liquibase is an interface for the sbt-liquibase package service")

    val status = TaskKey[Unit]("status", "Print count of unrun change sets")

    val tag = InputKey[Unit]("tag", "Tags the current database state for future rollback")

    val doc = TaskKey[Unit]("db-doc", "Generates Javadoc-like documentation based on current database and change log")

    val dropAll = TaskKey[Unit]("db-drop-all", "Drop all database objects owned by user")

    val changeLogFile = SettingKey[String]("changelog-file", "Liquibase changelog file.", ASetting)

    val dbUsername = SettingKey[String]("db-user", "Database user.", ASetting)

    val dbPassword = SettingKey[String]("db-password", "Database password.", ASetting)

    val dbURL = SettingKey[String]("db-url", "Database URL.", ASetting)

    val dbDriver = SettingKey[String]("db-driver", "Database driver.", ASetting)

    val liquibaseContext = SettingKey[String]("liquibase-context", "changeSet contexts to execute")

    lazy val liquibaseDatabase = TaskKey[Database]("liquibase-database", "the database")

    lazy val liquibaseInstance = TaskKey[Liquibase]("liquibase", "liquibase object")

    lazy val sbtLiquibaseSettings: Seq[Def.Setting[_]] = {

      Seq[Setting[_]](

        liquibaseContext in sbtLiquibase := "",

        liquibaseDatabase in sbtLiquibase := database((dbURL in sbtLiquibase).value, (dbUsername in sbtLiquibase).value, (dbPassword in sbtLiquibase).value, (dbDriver in sbtLiquibase).value, (dependencyClasspath in Compile).value),

        liquibaseInstance in sbtLiquibase := liquibaseInstance((changeLogFile in sbtLiquibase).value, (liquibaseDatabase in sbtLiquibase).value, (fullClasspath in Compile).value),

        update in sbtLiquibase := (liquibaseInstance in sbtLiquibase).value.execAndClose(_.update((liquibaseContext in sbtLiquibase).value)),

        status in sbtLiquibase := (liquibaseInstance in sbtLiquibase).value.execAndClose(_.reportStatus(true, (liquibaseContext in sbtLiquibase).value, new OutputStreamWriter(System.out))),

        run <<= run in Compile dependsOn (update in sbtLiquibase),
        initialize <<= (target) { target =>
          System.setProperty("java.library.path", target.getAbsolutePath)
        }
      )

    }

    def liquibaseInstance(changeLogFile: String, db: Database, dependencyClasspath: Classpath) = {

      assert(!changeLogFile.isEmpty, "Please set changeLogFile setting key.")

      val resourceAccessor = Files.exists(Paths.get(changeLogFile)) match {
        case true => new FileSystemResourceAccessor
        case false => new ClassLoaderResourceAccessor(ClasspathUtilities.toLoader(dependencyClasspath.map(_.data)))
      }

      new Liquibase(changeLogFile, resourceAccessor, db)
    }


    def database(dbURL: String, dbUsername: String, dbPassword: String,
                 dbDriver: String, dependencyClasspath: Classpath) = {

          CommandLineUtils.createDatabaseObject(
            ClasspathUtilities.toLoader(dependencyClasspath.map(_.data)),
            dbURL,
            dbUsername,
            dbPassword,
            dbDriver,
            null,
            null,
            false, // outputDefaultCatalog
            true, // outputDefaultSchema
            null, // databaseClass
            null, // driverPropertiesFile
            null, // propertyProviderClass
            null,
            null
          )
      }
    }

  import autoImport._
  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  override val projectSettings =
    inConfig(Compile)(sbtLiquibaseSettings)

}
