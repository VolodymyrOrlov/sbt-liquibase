# sbt-liquibase

SBT Plugin that wraps [Liquibase](http://www.liquibase.org/) framework. 

## Installation & Configuration

Add following lines in your _project/plugins.sbt_ file
```
addSbtPlugin("com.sungevity.sbt" % "sbt-liquibase" % "0.1.0")
```

Also set your databases using key

```
databases in sbtLiquibase := Map(
  "db-key-1" -> mysql("DB_URL", "DB_USERNAME", "DB_PASSWORD").withChangelog("changelog-file1"),
  "db-key-2" -> mysql("DB_URL", "DB_USERNAME", "DB_PASSWORD").withChangelog("changelog-file2"),
  ...
)
```

for example

```
databases in sbtLiquibase := Map(
  "local" -> mysql("jdbc:mysql://localhost/dev", "root", "").withChangelog("changelog.xml"),
  "remote" -> mysql("jdbc:mysql://somehost/prod", "root", "123").withChangelog("changelog.xml")
)
```

where _db-key-x_ is a unique key you use to refer to your database when running sbt-liquibase commands, _changelog-file1_ your changelog file. _changelog-file1_ should be either a name of the file from the classpath or an absolute path to the file on the local file system.

You can have as many entries in the map as you want.

## Usage

**sbt-liquibase** supports following commands of _Liquibase_:

* **sbt-liquibase::up** updates database to current version by applying all recent changes from your changelog file.
* **sbt-liquibase::down** rolls back the latest change.
* **sbt-liquibase::down \<N\>** rolls back N latest changes.
* **sbt-liquibase::down \<date\>** rolls back the database to the state it was in at the given date/time.
* **sbt-liquibase::down \<tag\>** rolls back the database to the state it was in when the tag was applied.
* **sbt-liquibase::status** outputs count of unrun change sets.
* **sbt-liquibase::tag \<tag name\>** "tags" the current database state for future rollback.
* **sbt-liquibase::locks** lists who currently has locks on the database changelog.

_sbt-liquibase::up_, _sbt-liquibase::down_, _sbt-liquibase::status_ and _sbt-liquibase::tag_ commands can be applied to all or a subset of databases listed in _databases in sbtLiquibase_ SBT setting. To select target database just list their key(s) after the command name:

```
sbt "sbt-liquibase::status local"
```
If you don't specify database instance the command will be applied to all configured instances.




