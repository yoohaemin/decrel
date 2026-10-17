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

Decrel is a Scala library for **dec**larative programming using **rel**ations between your data.

Read on to see how you can fetch data with automatic batching, parallelization, and caching while keeping your business logic clean and readable.

## Model

Decrel is a typed relation algebra with effect-specific interpreters. Declare domain relationships with `Single`, `Optional`, and `Many`; compose them into an expression; then provide `Proof` instances to execute that expression.

- `left >>: right` follows a relationship and returns the downstream result.
- `left <>: right` follows a relationship and retains the intermediate value alongside the downstream result.
- `left.zip(right)` (or `left & right`) combines independent relationships from the same input.

ZQuery and Fetch provide batching and request caching. Generic effects follow their own execution semantics. ScalaCheck and ZIO Test use `Gen.expand` to generate related values.

## Getting started

Add `com.yoohaemin:decrel-zquery` for ZIO or `com.yoohaemin:decrel-fetch` for Cats Effect, using the Scala-version suffix appropriate for your build. See the [installation guide](https://yoohaemin.github.io/decrel/guide/getting-started.html) for sbt and Mill coordinates.

The documentation contains compilation-tested examples:

- [ZQuery and Fetch quick starts](mdoc/docs/guide/getting-started.md)
- [Declarations, composition, and proof adaptation](mdoc/docs/guide/defining-relations.md)
- [Blog queries and generator integrations](mdoc/docs/showcase/README.md)

Bulk traversal preserves supported collection types such as `List` and `Vector`. Arrays require conversion. Inputs must be finite and preserve order, duplicates, and one output per input; arbitrary sets do not satisfy these requirements.

## Integration status

ZQuery is the primary integration, with tests for batching, caching, and overlapping-query deduplication. Fetch is secondary. Kyo integrations are experimental; Kyo cache integration is unfinished. Generator integrations do not promise global graph consistency across deeper traversals.

## Documentation checks

Run `sbt docs/mdoc` to compile and render the Scala examples. CI checks these on pull requests. Build-tool configuration fragments are labeled separately from Scala application examples.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

decrel is copyright Haemin Yoo, and is licensed under Mozilla Public License v2.0

`modules/core/src/main/scala/decrel/Zippable.scala` is based on https://github.com/zio/zio/blob/v2.0.2/core/shared/src/main/scala/zio/Zippable.scala , 
licensed under the Apache License v2.0
