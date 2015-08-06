package com.sungevity.sbt

import java.io.OutputStreamWriter
import java.nio.file.{Paths, Files}

import liquibase.Liquibase
import liquibase.database.Database
import liquibase.integration.commandline.CommandLineUtils
import liquibase.resource.{ResourceAccessor, ClassLoaderResourceAccessor, FileSystemResourceAccessor}
import sbt.KeyRanks._
import sbt.Keys._
import sbt._
import sbt.classpath.ClasspathUtilities

import com.sungevity.sbt.utils.IOUtils._
import com.sungevity.sbt.utils.DateUtils._
import com.sungevity.sbt.utils.StringUtils._

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

    val tag = InputKey[Unit]("tag", "'Tags' the database for future rollback")

    val locks = InputKey[Unit]("locks", "Lists who currently has locks on the database changelog.")

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

        update in sbtLiquibase := (selectInstances andThen foreachInstance(logKey andThen updateAction)) {
          (Def.spaceDelimited("<arg>").parsed, (liquibaseInstances in sbtLiquibase).value, streams.value)
        },

        updateAll in sbtLiquibase := foreachInstance(logKey andThen updateAction) {
          (Seq.empty, (liquibaseInstances in sbtLiquibase).value, streams.value)
        },

        tag in sbtLiquibase := (selectInstances andThen foreachInstance(logKey andThen tagAction)) {
          (Def.spaceDelimited("<arg>").parsed, (liquibaseInstances in sbtLiquibase).value, streams.value)
        },

        locks in sbtLiquibase := (selectInstances andThen foreachInstance(logKey andThen listLocksAction)) {
          (Def.spaceDelimited("<arg>").parsed, (liquibaseInstances in sbtLiquibase).value, streams.value)
        },

        downgrade in sbtLiquibase := (selectInstances andThen foreachInstance(logKey andThen rollbackAction)) {
          (Def.spaceDelimited("<arg>").parsed, (liquibaseInstances in sbtLiquibase).value, streams.value)
        },

        downgradeAll in sbtLiquibase := foreachInstance(logKey andThen rollbackAction) {
          (Seq.empty, (liquibaseInstances in sbtLiquibase).value, streams.value)
        },

        status in sbtLiquibase := (selectInstances andThen foreachInstance(logKey andThen statusAction)) {
          (Def.spaceDelimited("<arg>").parsed, (liquibaseInstances in sbtLiquibase).value, streams.value)
        },

        run <<= run in Compile dependsOn (updateAll in sbtLiquibase),
        initialize <<= (target) { target =>
          System.setProperty("java.library.path", target.getAbsolutePath)
        }

      )

    }

    type InstanceFilter = PartialFunction[(Seq[String], Map[String, Liquibase], TaskStreams), (Seq[String], Map[String, Liquibase], TaskStreams)]

    type Action = PartialFunction[(Seq[String], (String, Liquibase), TaskStreams), Unit]

    type ActionFilter = PartialFunction[(Seq[String], (String, Liquibase), TaskStreams), (Seq[String], (String, Liquibase), TaskStreams)]

    private def selectInstances: InstanceFilter = {
      case (args: Seq[String], instances: Map[String, Liquibase], streams) =>
        instances.filter(v => args.exists(v._1 == _)) match {
          case instances if !instances.isEmpty => (args.filter(!instances.contains(_)), instances, streams)
          case _ => (args, instances, streams)
        }
    }

    private def foreachInstance(action: PartialFunction[(Seq[String], (String, Liquibase), TaskStreams), Unit]): PartialFunction[(Seq[String], Map[String, Liquibase], TaskStreams), Unit] = {
      case (args: Seq[String], instances: Map[String, Liquibase], streams) =>
        instances.filter(v => args.exists(v._1 == _)) match {
          case instances if !instances.isEmpty => instances.map(action(args.filter(!instances.contains(_)), _, streams))
          case _ => instances.map(action(args, _, streams))
        }
    }

    private def logKey: ActionFilter = {

      case (args, (key, instance), streams) => {
        streams.log.info(s"[$key (${instance.getDatabase.getConnection.getURL})]")
        (args, (key, instance), streams)
      }

    }

    private def updateAction: Action = {

      case (_, (key, instance), streams) => instance.execAndClose(_.update(key))

    }

    private def rollbackAction: Action = {

      case (args, (key, instance), streams) if args.isEmpty =>
        instance.execAndClose(_.rollback(1, key)) // rollback 1 change
      case (args, (key, instance), streams) if !args.isEmpty && args.head.isNumber =>
        instance.execAndClose(_.rollback(args.head.toInt, key)) // rollback N changes
      case (args, (key, instance), streams) if !args.isEmpty && args.head.isISO8601Datetime =>
        instance.execAndClose(_.rollback(args.head.toDatetime.toDate, key)) // rollback to date
      case (args, (key, instance), streams) if !args.isEmpty =>
        instance.execAndClose(_.rollback(args.head, key)) // rollback to tag

    }

    private def tagAction: Action = {

      case (tag :: _, (key, instance), streams) => {
        instance.execAndClose{
          instance =>
            if(instance.tagExists(tag)) streams.log.error(s"Tag $tag already exists.")
            else instance.tag(tag)
        }
      }

      case (_, (key, instance), streams) => streams.log.error("Please specify tag name.")

    }

    private def listLocksAction: Action = {

      case (_, (key, instance), streams) => {
        instance.execAndClose(_.listLocks())
      }

    }

    private def statusAction: Action = {

      case (_, (key, instance), streams) => using(new OutputStreamWriter(System.out)) {
        instance.reportStatus(true, key, _)
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
            new ClassLoaderResourceAccessor(ClasspathUtilities.toLoader(dependencyClasspath.map(_.data))),
            dbURL,
            dbUsername,
            dbPassword,
            dbDriver,
            null,
            null,
            false,
            true,
            null,
            null,
            null,
            null,
            null,
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
