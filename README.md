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

where _db-key-x_ is a unique key you use to refer to your database when running sbt-liquibase commands, _changelog-file1_ your changelog file. _changelog-file1_ should be either a name of the file from the classpath or an absolute path to the file on the local file system.

You can have as many entries in the map as you want. _db-key-x_.







