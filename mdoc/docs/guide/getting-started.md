---
lang: en-US
title: Getting Started
---

# Adding decrel to your build

decrel is published for Scala 2.13 and 3, and for JVM and JS platforms.

## Release versions

### sbt

```scala
"com.yoohaemin" %% "decrel-core"       % "@RELEASEVERSION@" // Defines Relation and derivations
"com.yoohaemin" %% "decrel-zquery"     % "@RELEASEVERSION@" // Integration with ZQuery
"com.yoohaemin" %% "decrel-fetch"      % "@RELEASEVERSION@" // Integration with Fetch
"com.yoohaemin" %% "decrel-scalacheck" % "@RELEASEVERSION@" // Integration with ScalaCheck
"com.yoohaemin" %% "decrel-ziotest"    % "@RELEASEVERSION@" // Integration with ZIO-Test Gen 
"com.yoohaemin" %% "decrel-cats"       % "@RELEASEVERSION@" // Integration with F[_]: Monad
```

### mill

```scala
ivy"com.yoohaemin::decrel-core:@RELEASEVERSION@"       // Defines Relation and derivations
ivy"com.yoohaemin::decrel-zquery:@RELEASEVERSION@"     // Integration with ZQuery
ivy"com.yoohaemin::decrel-fetch:@RELEASEVERSION@"      // Integration with Fetch
ivy"com.yoohaemin::decrel-scalacheck:@RELEASEVERSION@" // Integration with ScalaCheck
ivy"com.yoohaemin::decrel-ziotest:@RELEASEVERSION@"    // Integration with ZIO-Test Gen 
ivy"com.yoohaemin::decrel-cats:@RELEASEVERSION@"       // Integration with F[_]: Monad
```

## Snapshot versions

### sbt

```scala
"com.yoohaemin" %% "decrel-core"       % "@SNAPSHOTVERSION@" // Defines Relation and derivations
"com.yoohaemin" %% "decrel-zquery"     % "@SNAPSHOTVERSION@" // Integration with ZQuery
"com.yoohaemin" %% "decrel-fetch"      % "@SNAPSHOTVERSION@" // Integration with Fetch
"com.yoohaemin" %% "decrel-scalacheck" % "@SNAPSHOTVERSION@" // Integration with ScalaCheck
"com.yoohaemin" %% "decrel-ziotest"    % "@SNAPSHOTVERSION@" // Integration with ZIO-Test Gen 
"com.yoohaemin" %% "decrel-cats"       % "@SNAPSHOTVERSION@" // Integration with F[_]: Monad
```

### mill

```scala
ivy"com.yoohaemin::decrel-core:@RELEASEVERSION@"       // Defines Relation and derivations
ivy"com.yoohaemin::decrel-zquery:@RELEASEVERSION@"     // Integration with ZQuery
ivy"com.yoohaemin::decrel-fetch:@RELEASEVERSION@"      // Integration with Fetch
ivy"com.yoohaemin::decrel-scalacheck:@RELEASEVERSION@" // Integration with ScalaCheck
ivy"com.yoohaemin::decrel-ziotest:@RELEASEVERSION@"    // Integration with ZIO-Test Gen 
ivy"com.yoohaemin::decrel-cats:@RELEASEVERSION@"       // Integration with F[_]: Monad
```

### Snapshot resolver (sbt)

If you are using an older (< 1.7.0) version of sbt, you might also need to add a resolver.

```scala
resolvers += 
  "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
```

## What to pick?

It depends on what you need from decrel.

### `decrel-core`

You would normally not need to specify `decrel-core` as a dependency, but it would be enough to 
specify one of the others and get this in as a transitive dependency.

Declare this dependency if:
- You have a module that only contains purely the domain model without actual business logic.
- You can't rely on dependencies that are pulled in transitively. (I've heard bazel is like that)

### `decrel-zquery`

Declare this dependency if you want to access datasources with relations, and your application 
is based on ZIO.

### `decrel-fetch`

Declare this dependency if you want to access datasources with relations, and your application
is based on cats-effect (whether it is tagless-final style or directly on `cats.IO`).

### `decrel-scalacheck`

Declare this dependency if you want to generate random data with relations, and your tests use 
`scalacheck` generators.

### `decrel-ziotest`

Declare this dependency if you want to generate random data with relations, and your tests use
`zio-test` generators.

### `decrel-cats`

Declare this dependency if you have some datatype outside the above four that you want to use with
decrel, and if that datatype has `cats.Monad` implemented. (see `decrel-fetch` for examples on
how to do this)