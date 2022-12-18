---
lang: en-US
title: Getting Started
---

Decrel lets you define 

## Showcase

### Business logic (simple)

Decrel provides a simple syntax to describe data access; there is no more need to declare methods for each data access pattern.

In this example, I am trying to fetch a `Rental` object, plus the associated `Book` and `User` object from a given `rentalId`.

```scala
for {
  rental <- rentals.get(rentalId)
  book <- books.get(rental.bookId)
  user <- users.get(rental.userId)

  // ... do something with 3 objets
```

becomes

<CodeGroup>
  <CodeGroupItem title="ZQuery (ZIO)" active>

```scala:no-line-numbers
for {
  (rental, book, user) <-
    (Rental.fetch :>: (Rental.book & Rental.user)).toZIO(rentalId)

  // ... do something with 3 objets
```
  </CodeGroupItem>
  <CodeGroupItem title="Fetch (cats-effect)">

```scala:no-line-numbers
for {
  (rental, book, user) <-
    (Rental.fetch :>: (Rental.book & Rental.user)).toF(rentalId)

  // ... do something with 3 objets
```
  </CodeGroupItem>
</CodeGroup>

With the Decrel example, two queries to fetch the book and the user are parallelized by default.

### Business logic (slightly more complex)

The difference becomes substantial for even slightly complex queries.

In this example, I am trying to fetch the list of books the user is currently renting, identified by the given `userId`.

<CodeGroup>
  <CodeGroupItem title="Inefficient (N+1 problem) but simple" active>

```scala:no-line-numbers
for {
  user <- users.get(userId)
  currentRentals <- rentals.currentForUser(user)
  books <- currentRentals.traverse(rental => books.get(rental.bookId))

  // ... do something with books
```
  </CodeGroupItem>
  <CodeGroupItem title="Efficient but requires dedicated method">

```scala:no-line-numbers
for {
  user <- users.get(userId)
  currentRentals <- rentals.currentForUser(user)
  books <- books.getForRentals(currentRentals)

  // ... do something with books
```
  </CodeGroupItem>
</CodeGroup>

In the inefficient example, only the simple `books.get` operation was used, but calls to the datasource are not batched, resulting in an inefficient query.

In the efficient example, a custom method (`getForRentals`) was used, requiring additional implementation and tests for your application.

<CodeGroup>
  <CodeGroupItem title="ZQuery (ZIO)" active>

```scala:no-line-numbers
for {
  books <- (User.fetch >>: User.currentRentals >>: Rental.book).toZIO(userId)

  // ... do something with books
```
  </CodeGroupItem>
  <CodeGroupItem title="Fetch (cats-effect)">

```scala:no-line-numbers
for {
  books <- (User.fetch >>: User.currentRentals >>: Rental.book).toF(userId)

  // ... do something with books
```
  </CodeGroupItem>
</CodeGroup>

With Decrel, your queries remain efficient while maintaining clarity.
Datasource accesses are automatically batched, deduplicated, and parallelized by the underlying ZQuery or Fetch library.

### Implementing REST APIs

Decrel removes the need to maintain duplicate versions of the same method to support different data requirement patterns.

In this example, we expose a `create rental` route that returns additional information on top of created rental, based on the `expand` query parameter:

```scala:no-line-numbers
val route = HttpRoutes.of {
  case req @ GET -> POST / "rental" :? Expand(expand) =>
    val (userId, bookId) = extractDetails(req)

    if (expand) // Need rental and book info
      rentalService
        .createRentalReturningExpanded(userId, bookId)
        .map(ExpandedRentalResponse.make)
        .flatMap(Ok(_))
    else // Need only rental info
      rentalService
        .createRental(userId, bookId) // Need to maintain additional method
        .map(RentalResponse.make)
        .flatMap(Ok(_))
}
```

Notice that without the ability to abstract over return types, there is no choice but to duplicate methods that essentially does the same thing.

```scala:no-line-numbers
val route = HttpRoutes.of {
  case req @ GET -> POST / "rental" :? Expand(expand) =>
    val (userId, bookId) = extractDetails(req)

    if (expand) // Need rental and book info
      rentalService
        .createRental(
          userId,
          bookId,
          (Rental.self & Rental.user & Rental.book).reify
        ) // Returns `(Rental, User, Book)`
        .map(ExpandedRentalResponse.make)
        .flatMap(Ok(_))
    else // Need only rental info
      rentalService
        .createRental( // Same method
          userId,
          bookId,
          Rental.self.reify
        ) // Returns `Rental`
        .map(RentalResponse.make)
        .flatMap(Ok(_))
}
```

With Decrel, your methods can be polymorphic, letting the callsite decide what the exact return type would be. This removes the need to duplicate methods.

### Caliban integration

Decrel works as a simple builder for ZQuery values.

```scala:no-line-numbers
def getRental(id: Rental.Id): Query[Err, Rental] = 
  Rental.fetch.toQuery(id).map { domainRental =>
    protocol.Rental(
      id = domainRental.id,
      book = (Rental.fetch >>: Rental.book).toQuery(id),
      user = (Rental.fetch >>: Rental.user).toQuery(id)
    )
  }

Queries(
  getRental = getRental
)
```

Sounds interesting? Head on to the getting started page!

# Phrases

I will be using the following words/phrases in this documentation.

- domain declaration site
  - Places you declare your domain classes. This is where the relations should live too.
- call site (callsite)
  - The place you compose your domains and turn them into values
- implementation site
  - THe place you define implementations for each of your relations

# How to use

## Declaring relations

Declare your relations next to your domain classes:

```scala
case class Book(id: Book.Id, title: String)
object Book {
  case class Id(value: String)
  
  // Add these
  case object fetch         extends Relation.Single[Book.Id, Book]
  case object self          extends Relation.Self[Book]
  case object currentRental extends Relation.Optional[Book, Rental]
  case object allRentals    extends Relation.Many[Book, List, Rental]
}

case class User(id: User.Id)
object User {
  case class Id(value: String)

  // And here
  case object fetch          extends Relation.Single[User.Id, User]
  case object self           extends Relation.Self[User]
  case object currentRentals extends Relation.Many[User, List, Rental]
  case object allRentals     extends Relation.Many[User, List, Rental]
}

case class Rental(id: Rental.Id, bookId: Book.Id, rentDate: LocalDate)
object Rental {
  case class Id(value: String)

  // Also here
  // Also here
  case object fetch extends Relation.Single[Rental.Id, Rental]
  case object self  extends Relation.Self[Rental]
  case object book  extends Relation.Single[Rental, Book]
  case object user  extends Relation.Single[Rental, User]
}
```

Here are the rules:
- Name the relations using the target type name and additional details,
  if applicable. 
- 
- There are two special cases:
  - `fetch` is a relation between an object's id and the object. It's 

You can see 4 different kinds of relations.
- `Relation.Single`


In this example, 

`Book.fetch` is a single relation (one `Book.Id` means exactly one `Book`)

`Book.self` is a self relation (one `Book` is exactly one `Book`, which is itself)

`Book.currentRental` is an optional relation (one `Book` means at most related `Rental` 
object)

`Book.allPastRentals` is a many relation (one `Book` means `List[Rental]`)


## Accessing your datasource

### Relation traversals

Now that your domain forms a directed graph, you can traverse the graph and
describe what additional data you want from the starting point you have.

For example, say you have a value of type `Book`, and you want to get 



You can compose your relations using `&`, `>>:`, `:>:` operators.



### If your codebase is based on ZIO

### If your codebase is based on ZIO

- Callsite

```scala

```

- Implementation

For each relations you want to reify, you need to 


### If your codebase is based on cats-effect

[//]: # (TODO write this section)

## Composing your generators

[//]: # (TODO write this section)

### If your tests use `Gen` from ZIO Test

[//]: # (TODO write this section)

### If your tests use `Gen` from ScalaCheck

[//]: # (TODO write this section)

# Underlying idea

There are 2 ideas.

- discrepancy between the concept of an operation, and real-life methods
- we can utilize ideas that domain models

# Other Topics

## I want to make runtime datasource access faster for a hot path

If if you want to override the default runtime behavior for datasource access, 
here's how you do it.

### In the callsite

```scala
// TODO implement
```

### In the implementation site

```scala

```scala
// TODO implement
```

## Can we derive the relations?

Some people have pointed out the similarities of relations with optics, which I agree.
Naturally, the question follows that if we can derive the relations. Unfortunately, 
I don't think it's possible or a good idea, purely because existing code doesn't have 
enough information to do so.

Specifically, referring to the example in the tutorial page, it is impossible to 
mechanically infer `Book.currentRental` or `Book.allPastRentals` as relationship 
between `Book` and `Rental`.

This leads to a different point: explicitly added relations work as documentation in
to your codebase, as opposed to optics declaration, which adds no new information.

## How is this library implemented?

Here's a tour of the library's structure.




# Introduction

Welcome to the documentation of Decrel.

Decrel is a library to let you declare relations between data models, and utilize them in Scala code when possible.

# TL;DR

## Declaring relations

Declare your relations like this:

[//]: # (TODO write this section)


## Accessing your datasource

### If your codebase is based on ZIO

### If your codebase is based on cats-effect

## Composing your 

### If your tests use `Gen` from ZIO Test

### If your tests use `Gen` from ScalaCheck

# Quickstart

# The big idea

There are 2 ideas.

- discrepancy between the concept of an operation, and real-life methods
- we can utilize ideas that domain models 

# Implementation


# Generators


Guide
Idea.
Definition DSL
Composition DSL
ZQuery.
Gen.

How-To (cookbook-style)
Business logic
REST API
Caliban


