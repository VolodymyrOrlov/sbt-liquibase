package com.sungevity.sbt

import java.io.{File, OutputStreamWriter}
import java.nio.file.{Paths, Path, StandardCopyOption, Files}

import liquibase.Liquibase
import liquibase.database.Database
import liquibase.integration.commandline.CommandLineUtils
import liquibase.resource.{ResourceAccessor, ClassLoaderResourceAccessor, FileSystemResourceAccessor}
import sbt.KeyRanks._
import sbt.Keys._
import sbt._
import sbt.classpath.ClasspathUtilities

object SbtLiquibase extends AutoPlugin {

  implicit class RichLiquibase(liquibase: Liquibase) {
    def execAndClose(f: (Liquibase) => Unit): Unit = {
      try { f(liquibase) } finally { liquibase.getDatabase.close() }
    }
  }

  object autoImport {


    def mysql(url: String, username: String, password: String): LiquibaseTarget =
      LiquibaseTarget(url, username, password, "com.mysql.jdbc.Driver", None)

    case class LiquibaseTarget(url: String, username: String, password: String, driver: String, changelog: Option[String]) {

      def withChangelog(url: String) = this.copy(changelog = Some(url))

    }

    val sbtLiquibase = TaskKey[Unit]("sbt-liquibase", "Parent key")

    val update = InputKey[Unit]("up", "Run a liquibase migration")

    val updateAll = TaskKey[Unit]("up-all", "Run a liquibase migration")

    val downgrade = InputKey[Unit]("down", "Rolls back the database to the the state is was when the tag was applied")

    val downgradeAll = InputKey[Unit]("down-all", "Rolls back the database to the the state is was when the tag was applied")

    val status = InputKey[Unit]("status", "Print count of unrun change sets")

    val databases = SettingKey[Map[String, LiquibaseTarget]]("databases", "List of target databases.", ASetting)

    lazy val liquibaseInstances = TaskKey[Map[String, Liquibase]]("liquibase", "liquibase object")

    lazy val sbtLiquibaseSettings: Seq[Def.Setting[_]] = {

      Seq[Setting[_]](


        liquibaseInstances in sbtLiquibase := (databases in sbtLiquibase).value.collect {
          case (key, LiquibaseTarget(url, username, password, driver, changelog)) =>
            key -> liquibaseInstance(changelog.getOrElse("changelog.xml"), database(url, username, password, driver, (dependencyClasspath in Compile).value), (fullClasspath in Compile).value)
        },

        update in sbtLiquibase := forSelected(Def.spaceDelimited("<arg>").parsed, (liquibaseInstances in sbtLiquibase).value){
          _.execAndClose(_.update(""))
        },

        downgrade in sbtLiquibase := forSelected(Def.spaceDelimited("<arg>").parsed, (liquibaseInstances in sbtLiquibase).value){
          _.execAndClose(_.rollback(1, ""))
        },

        downgradeAll in sbtLiquibase := forAll((liquibaseInstances in sbtLiquibase).value){
          _.execAndClose(_.rollback(1, ""))
        },

        updateAll in sbtLiquibase := forAll((liquibaseInstances in sbtLiquibase).value){
          _.execAndClose(_.update(""))
        },

        status in sbtLiquibase := forSelected(Def.spaceDelimited("<arg>").parsed, (liquibaseInstances in sbtLiquibase).value){
          _.reportStatus(true, "", new OutputStreamWriter(System.out))
        },

        run <<= run in Compile dependsOn (updateAll in sbtLiquibase),
        initialize <<= (target) { target =>
          System.setProperty("java.library.path", target.getAbsolutePath)
        }
      )

    }

    private def forSelected(selectedTargets: Seq[String], instances: Map[String, Liquibase])(action: (Liquibase) => Unit) {
      instances.filter(v => selectedTargets.exists(v._1 == _)).collect {
        case (key, instance) =>
          action(instance)
      }
    }

    private def forAll(instances: Map[String, Liquibase])(action: (Liquibase) => Unit) {

      instances.collect {
        case (key, instance) =>
          action(instance)
      }

    }

    private def identifyChangelogLocation(url: String, cp: Classpath): (String, ResourceAccessor) = {

      val resourceAccessor = Files.exists(Paths.get(url)) match {
        case true => new FileSystemResourceAccessor
        case false => new ClassLoaderResourceAccessor(ClasspathUtilities.toLoader(cp.map(_.data)))
      }

      (url, resourceAccessor)

    }

    private def liquibaseInstance(changelogURL: String, db: Database, dependencyClasspath: Classpath) = {

      val (changelog, resourceAccessor) = identifyChangelogLocation(changelogURL, dependencyClasspath)

      new Liquibase(changelog, resourceAccessor, db)
    }

    private def database(dbURL: String, dbUsername: String, dbPassword: String,
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
