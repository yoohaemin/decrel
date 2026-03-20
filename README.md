# Decrel

[![Continuous Integration](https://github.com/yoohaemin/decrel/actions/workflows/ci.yml/badge.svg)](https://github.com/yoohaemin/decrel/actions/workflows/ci.yml)
[![Project stage: Active][project-stage-badge: Active]](#)
[![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases]
[![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots]

[project-stage-badge: Active]: https://img.shields.io/badge/Project%20Stage-Active-blue.svg
[Link-SonatypeReleases]: https://s01.oss.sonatype.org/content/repositories/releases/com/yoohaemin/decrel-core_3/ "Sonatype Releases"
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/s01.oss.sonatype.org/com.yoohaemin/decrel-core_3.svg "Sonatype Releases"
[Link-SonatypeSnapshots]: https://s01.oss.sonatype.org/content/repositories/snapshots/com/yoohaemin/decrel-core_3/ "Sonatype Snapshots"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/s01.oss.sonatype.org/com.yoohaemin/decrel-core_3.svg "Sonatype Snapshots"

Decrel is a Scala library for declarative programming with relations.

Its core job is to let application code express composition of joins directly, then execute that relation graph efficiently through integrations such as ZQuery and Fetch.

## Why use it?

Most applications repeatedly need to:

- follow several joins from one starting value
- preserve optional and one-to-many branches
- avoid N+1 fetches
- keep service code readable

Without a dedicated abstraction, that logic spreads into services and repositories as nested effect code, ad hoc batching, and duplicated orchestration.

Decrel turns those joins into first-class values.

```scala
val checkoutView =
  Order.customer & (Order.items <>: (OrderItem.product & OrderItem.price))
```

That relation says what data the application needs. The runtime integration handles batching, deduplication, and caching.

## Production-oriented

This repository already provides:

- published artifacts
- Scala 2.13 and 3 cross-building
- JVM and JS support for the main modules
- CI across multiple Scala versions
- tests for composition semantics, batching, deduplication, and caching
- generated docs and API reference

## Modules

```scala
"com.yoohaemin" %% "decrel-core"       % "x.y.z"
"com.yoohaemin" %% "decrel-zquery"     % "x.y.z"
"com.yoohaemin" %% "decrel-fetch"      % "x.y.z"
"com.yoohaemin" %% "decrel-scalacheck" % "x.y.z" % Test
"com.yoohaemin" %% "decrel-ziotest"    % "x.y.z" % Test
"com.yoohaemin" %% "decrel-cats"       % "x.y.z"
"com.yoohaemin" %% "decrel-kyo"        % "x.y.z"
"com.yoohaemin" %% "decrel-kyo-batch"  % "x.y.z"
```

## Documentation

Full documentation lives at <https://yoohaemin.github.io/decrel>.

The docs cover:

- the core idea of composition of joins
- production-readiness guidance
- relation composition and proof APIs
- cache and custom relations
- example app structure with dependency injection
- generated Scaladoc for the public modules

## License

decrel is copyright Haemin Yoo, and is licensed under Mozilla Public License v2.0

`core/src/main/scala/decrel/Zippable.scala` is based on <https://github.com/zio/zio/blob/v2.0.2/core/shared/src/main/scala/zio/Zippable.scala>,
licensed under the Apache License v2.0.
