## Decrel

[![Continuous Integration](https://github.com/yoohaemin/decrel/actions/workflows/ci.yml/badge.svg)](https://github.com/yoohaemin/decrel/actions/workflows/ci.yml)
[![Project stage: Experimental][project-stage-badge: Experimental]](#) 
[![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases]
[![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots]

[project-stage-badge: Experimental]: https://img.shields.io/badge/Project%20Stage-Experimental-yellow.svg
[Link-SonatypeReleases]: https://s01.oss.sonatype.org/content/repositories/releases/com/yoohaemin/decrel-core_3/ "Sonatype Releases"
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/s01.oss.sonatype.org/com.yoohaemin/decrel-core_3.svg "Sonatype Releases"
[Link-SonatypeSnapshots]: https://s01.oss.sonatype.org/content/repositories/snapshots/com/yoohaemin/decrel-core_3/ "Sonatype Snapshots"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/s01.oss.sonatype.org/com.yoohaemin/decrel-core_3.svg "Sonatype Snapshots"

Decrel is a library for **dec**larative programming using **rel**ations between your data.

## Usecases

For a given domain:
```scala
case class Book(id: Book.Id, name: String, author: Author.Id)
object Book {
  case class Id(value: String)
}

case class Author(id: Author.Id, name: String, books: List[Book.Id])
object Author {
  case class Id(value: String)
}
```

You can declare relations between your entities by extending the appropriate `Relation` types.

```scala
case class Book(id: Book.Id, name: String, author: Author.Id)
object Book {
  case class Id(value: String)
  
  // Define a relation to itself by extending Relation.Self
  // This is useful when composing with other relations later
  case object self extends Relation.Self[Book]
  
  // Define the relation and the kind of relation that exists between two entities
  // Relation.Single means for a book there is a single author
  // depending on your domain, you may want to choose different kinds
  case object author extends Relation.Single[Book, Author]
}

case class Author(id: Author.Id, name: String, books: List[Book.Id])
object Author {
  case class Id(value: String)
  
  case object self extends Relation.Self[Author]

  // Extending Relation.Many means for a given author, there is a list of books
  case object book extends Relation.Many[Author, List, Book]
}
```

### Accessing your data source

To express "given a book, get the author && all the books written by them", looks like this:
```scala
val getAuthorAndTheirBooks = Book.author :>: Author.books
```

But how would you run this with an instance of Book that you have?
```scala
val exampleBook = Book(Book.Id("book_id"), "bookname", Author.Id("author_id"))
```
If your application uses [ZIO](https://github.com/zio/zio), there is an integration with ZIO through [ZQuery](https://github.com/zio/zio-query):
```scala
import decrel.reify.zquery._
import proofs._  // Datasource implementation defined elsewhere in your code

// Exception is user defined in the datasource implementation
val output: zio.IO[AppError, (Author, List[Book])] = 
  getAuthorAndTheirBooks.toZIO(exampleBook)
```

Or if you use [cats-effect](https://github.com/typelevel/cats-effect), there is an integration with any effect type
that implements `cats.effect.Concurrent` (including `cats.effect.IO`) through the [Fetch](https://github.com/47degrees/fetch) library:
```scala
class BookServiceImpl[F[_]](
  // contains your datasource implementations
  proofs: Proofs[F]
) {
  import proofs._

  val output: F[(Author, List[Book])] =
    getAuthorAndTheirBooks.toF(exampleBook) 
}
```

By default, queries made by decrel will be efficiently batched and deduplicated, thanks to the underlying[^1] `ZQuery` or `Fetch`
data types which are based on [Haxl](https://github.com/facebook/Haxl).

[^1]: You are not required to interact with ZQuery or Fetch datatypes in your application -- simply use the APIs that exposes `ZIO` or `F[_]`.


### Generating mock data

You can combine generators defined using scalacheck or zio-test. [^2]

[^2]: Even if your testing library is not supported, adding one is done easily. See `decrel.scalacheck.gen` or `decrel.ziotest.gen`.
The implementation code should work for a different `Gen` type with minimal changes.

To express generating an author and a list of books by the author, you can write the following:

```scala
val authorAndBooks: Gen[(Author, Book)] =
  gen.author // This is your existing generator for Author
    .expand(Author.self & Author.books) // Give me the generated author,
                                        // additionally list of books for the author
```

Now you can simply use the composed generator in your test suite.

The benefit of using decrel to compose generators is twofold:
- less boilerplate compared to specifying generators one-by-one (especially when options/lists are involved)
- values generated are more consistent compared to generating values independently
  - In this case, all books will have the `authorId` fields set to the generated author.

# Notice to all Scala 3 users

Any method that requires an implicit (given) instance of `Proof` needs to be called against a `val` value.

See [this commit](https://github.com/yoohaemin/decrel/commit/8b836b5c41b58a77d791c36e8b81e4f6e979e297) for examples.

# Acknowledgements

Thanks to [@ghostdogpr](https://github.com/ghostdogpr) for critical piece of insight regarding the design of the api and the initial feedback.

Thanks to [@benrbray](https://github.com/benrbray) for all the helpful discussions.

Thanks to [@benetis](https://github.com/benetis) for pointing out there was a problem that needs fixing.

Thanks to all of my friends and colleagues who provided valuable initial feedback.

# License

decrel is copyright Haemin Yoo, and is licensed under Mozilla Public License v2.0

`modules/core/src/main/scala/decrel/Zippable.scala` is based on https://github.com/zio/zio/blob/v2.0.2/core/shared/src/main/scala/zio/Zippable.scala , 
licensed under the Apache License v2.0
