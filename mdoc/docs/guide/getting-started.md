---
lang: en-US
title: Getting Started
---

# Adding decrel to your build

decrel is published for Scala 2.13 and 3, and for JVM and JS platforms.

## Release versions

```scala
"com.yoohaemin" %% "decrel-core"       % "@RELEASEVERSION@" // Defines Relation and derivations
"com.yoohaemin" %% "decrel-zquery"     % "@RELEASEVERSION@" // Integration with ZQuery
"com.yoohaemin" %% "decrel-fetch"      % "@RELEASEVERSION@" // Integration with Fetch
"com.yoohaemin" %% "decrel-scalacheck" % "@RELEASEVERSION@" // Integration with ScalaCheck
"com.yoohaemin" %% "decrel-ziotest"    % "@RELEASEVERSION@" // Integration with ZIO-Test Gen 
"com.yoohaemin" %% "decrel-cats"       % "@RELEASEVERSION@" // Integration with F[_]: Monad
```

## Snapshot versions

```scala
"com.yoohaemin" %% "decrel-core"       % "@SNAPSHOTVERSION@" // Defines Relation and derivations
"com.yoohaemin" %% "decrel-zquery"     % "@SNAPSHOTVERSION@" // Integration with ZQuery
"com.yoohaemin" %% "decrel-fetch"      % "@SNAPSHOTVERSION@" // Integration with Fetch
"com.yoohaemin" %% "decrel-scalacheck" % "@SNAPSHOTVERSION@" // Integration with ScalaCheck
"com.yoohaemin" %% "decrel-ziotest"    % "@SNAPSHOTVERSION@" // Integration with ZIO-Test Gen 
"com.yoohaemin" %% "decrel-cats"       % "@SNAPSHOTVERSION@" // Integration with F[_]: Monad
```

If you are using an older (< 1.7.0) version of sbt, you might also need to add a resolver.

```scala
resolvers += 
  "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
```