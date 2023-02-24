---
lang: en-US
title: Getting Started
---

# Showcase

## Datasource access

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

```scala
for {
  (rental, book, user) <- (Rental.fetch :>: (Rental.book & Rental.user)).toZIO(rentalId)

  // ... do something with 3 objets
```
  </CodeGroupItem>
  <CodeGroupItem title="Fetch (cats-effect)">

```scala
for {
  (rental, book, user) <- (Rental.fetch :>: (Rental.book & Rental.user)).toF(rentalId)

  // ... do something with 3 objets
```
  </CodeGroupItem>
</CodeGroup>

With the Decrel example, two queries to fetch the book and the user are parallelized by default.

### Business logic (complex)

In this example, I am trying to fetch the list of books the user is currently renting, identified by the given `userId`.

<CodeGroup>
  <CodeGroupItem title="Inefficient (N+1 problem) but simple" active>

```scala
for {
  user <- users.get(userId)
  currentRentals <- rentals.currentForUser(user)
  books <- currentRentals.traverse(rental => books.get(rental.bookId))

  // ... do something with books
```
  </CodeGroupItem>
  <CodeGroupItem title="Efficient but requires dedicated method">

```scala
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

```scala
for {
  books <- (User.fetch >>: User.currentRentals >>: Rental.book).toZIO(userId)

  // ... do something with books
```
  </CodeGroupItem>
  <CodeGroupItem title="Fetch (cats-effect)">

```scala
for {
  books <- (User.fetch >>: User.currentRentals >>: Rental.book).toF(userId)

  // ... do something with books
```
  </CodeGroupItem>
</CodeGroup>

With Decrel, your queries remain efficient while maintaining clarity.
Datasource accesses are automatically batched, deduplicated, and parallelized by the underlying ZQuery or Fetch library.

### Implementing REST APIs

Decrel removes the need to maintain duplicate versions of the same method to support different data requirement patterns for a single operation.

In this example, we expose a `create rental` route that returns additional information on top of created rental, based on the `expand` query parameter:

```scala:{7,12}
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

```scala:{7,16}
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

You can easily build ZQuery values for your Caliban protocols.

```scala
def getRental(id: Rental.Id): zio.query.Query[Err, Rental] = 
  Rental.fetch.toQuery(id).map { domainRental =>
    protocol.Rental(
      id = domainRental.id,
      book = (Rental.fetch >>: Rental.book).toQuery(id).map(protocol.Book.make),
      user = (Rental.fetch >>: Rental.user).toQuery(id).map(protocol.User.make)
    )
  }

Queries(
  getRental = getRental
)
```
