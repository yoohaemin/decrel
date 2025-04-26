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

## Problem Statement

Fetching data from datasources is an extremely common operation in applications. Usually, this is done by calling methods or functions to fetch data in an imperative manner.

This pattern is universal across languages and frameworks, from JavaScript to Haskell, and from Spring to Django:

```scala
val bookId: Book.Id = ???

for {
  book <- bookRepository.getById(bookId)
  author <- authorRepository.getById(book.authorId)
  price <- priceService.getPrice(book.id)
  // ... do your stuff with book, author, and price
} yield ()
```

This code works, but has several hidden issues:

* **Sequential Execution**: Fetching the author and price are independent operations but run sequentially, creating unnecessary latency
* **N+1 Query Problem**: If you have multiple books, you'll end up calling each API N times, or need to manually implement "joins"
* **No Caching by Default**: Cache access typically requires additional code for each operation
* **Complexity Escalation**: Combining these concerns quickly increases code complexity

## What is Decrel?

Decrel enables:

### 1. Declarative Data Access

Express relationships between your data models as first-class values:
* "A `Book` has one `Author`"
* "A `User` may or may not have a `PremiumSubscription`"

```scala
object Book {
  object author extends Relation.Single[Book, Author]
}

object User {
  object subscription extends Relation.Optional[User, PremiumSubscription]
}
```

### 2. Implementation Control

You decide how to fulfill each relation with actual data access logic:

```scala
// ZIO implementation
implementSingleDatasource(Book.author) { books =>
  ZIO.succeed(books.map(book => book -> authorMap(book.authorId)))
}

// Cats Effect implementation
implementSingleDatasource(Book.author) { books =>
  IO.pure(books.map(book => book -> authorMap(book.authorId)))
}
```

### 3. Composition of Relations

Combine simple relations to express complex access patterns:

```scala
// Get the publisher of a book's author (sequential composition)
val bookAuthorPublisher = Book.author <>: Author.publisher

// Get both the author and the price of a book (parallel composition)
val bookDetails = Book.author & Book.price
```

### 4. Efficient Execution

The composed relations are efficiently executed against your datasource, with automatic batching and parallelization through integrations with ZQuery and Fetch.

### 5. Testing Support

The same relations can be used to generate random test data:

```scala
// For ScalaCheck
val bookGen: Gen[Book] = Book.arbitrary
val bookWithAuthorGen: Gen[(Book, Author)] = (Book.Self & Book.author).arbitrary

// For ZIO Test
val bookGen: Gen[Any, Book] = Book.gen
val bookWithAuthorGen: Gen[Any, (Book, Author)] = (Book.Self & Book.author).gen
```

## Examples

With Decrel, you can express the same operation more clearly and efficiently:

```scala
val bookId: Book.Id = ???

for {
  (book: Book, author: Author, price: Price) <-
    (Book.Self & Book.author & Book.price).toZIO(bookId)
                                       // ^ .toF for cats-effect
  // ... do your stuff with book, author, and price
} yield ()
```

### Batching and Parallelism by Default, not an Optimization

Decrel integrates with ZQuery (ZIO) or Fetch (cats-effect) to provide efficient batching and parallelism by default. Independent data fetching operations (like getting author and price) run concurrently.

### No N+1 Problem

When dealing with multiple items, Decrel handles batching efficiently:

```scala
val bookIds: List[Book.Id] = ???

for {
  bookDetails: List[(Book, Author, Price)] <- 
    (Book.Self & Book.author & Book.price).toZIOMany(bookIds)
                                       // ^ .toFMany for cats-effect
  // ... do your stuff with the list
} yield ()
```

The return type is a list of tuples, making it easy to process the results. Decrel preserves your collection type - if you use `Vector`, you get `Vector` back; same works for `List`, `Array`, `zio.Chunk` etc.

Underlying calls are automatically batched and parallelized. With proper batch implementations of your datasources, this code will call the underlying APIs at most 3 times, regardless of how many books you're retrieving.

### Advanced Optimization and Caching

Decrel gives you complete control over how data is accessed. You can implement sophisticated caching strategies.

Refer to the below pseudocode to see an example, showcasing what you can do with decrel:

```scala
object BookRelations extends zquery[Any] {
  implicit val bookAuthorProof: Proof.Single[Book.author.type, Book, Nothing, Author] =
    implementSingleDatasource(Book.author) { books =>
      for {
        // Check cache first
        cachedAuthors <- checkCache(books.map(_.authorId))
        // Find which IDs aren't in cache
        missingIds = books.map(_.authorId).filterNot(cachedAuthors.contains)
        // Fetch missing authors from DB
        fetchedAuthors <- if (missingIds.isEmpty) ZIO.succeed(Chunk.empty) else fetchAuthors(missingIds) 
        // Update cache with newly fetched authors
        _ <- updateCache(fetchedAuthors)
        // Combine cached and fetched results
        results = books.map(book => book -> (cachedAuthors.get(book.authorId) orElse fetchedAuthors.get(book.authorId)).get)
      } yield results
    }
}
```

Your domain logic remains clean and unaware of these optimizations.

## Getting Started

Add Decrel to your build:

[![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases]

```scala
// For ZIO users
"com.yoohaemin" %% "decrel-zquery" % "x.y.z"

// For Cats Effect users
"com.yoohaemin" %% "decrel-fetch" % "x.y.z"

// For testing
"com.yoohaemin" %% "decrel-scalacheck" % "x.y.z" % Test
"com.yoohaemin" %% "decrel-ziotest" % "x.y.z" % Test
```

## Documentation

For comprehensive documentation, examples, and guides, please visit the [Decrel Documentation](https://yoohaemin.github.io/decrel).

## Conceptual Explanation

On a fundamental level, Decrel is a structured way to compose `flatMap`/`traverse` operations:

* Relations are like arrows with three "kinds" — Single, Optional, and Many
* You provide implementations as functions: `In => F[Kind[Out]]` (where `Kind` is `Id`, `Option`, or `Collection[A]`)
* Decrel handles the composition of these operations according to the relation structure

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

decrel is copyright Haemin Yoo, and is licensed under Mozilla Public License v2.0

`modules/core/src/main/scala/decrel/Zippable.scala` is based on https://github.com/zio/zio/blob/v2.0.2/core/shared/src/main/scala/zio/Zippable.scala , 
licensed under the Apache License v2.0