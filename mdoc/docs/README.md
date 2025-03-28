---
lang: en-US
home: true
heroText: decrel
tagline: Declarative data relation library for Scala
actionText: Get Started →
actionLink: /guide/getting-started
features:
- title: Declarative
  details: Define relationships between data types once and reuse them across your application. Let decrel handle the implementation details.
- title: Efficient by Default
  details: Automatically batch multiple queries and run independent operations in parallel. No more N+1 query problems.
- title: Ecosystem Integration
  details: Works with both ZIO and cats-effect. Use with ZQuery, Fetch, or extend to your own effect system.
- title: Type-Safe
  details: Leverage Scala's type system for compile-time guarantees about your data access patterns.
- title: Composition-First
  details: Build complex data access patterns by composing simple relations with intuitive operators.
- title: Testable
  details: The same relations used for data fetching can generate test data with ScalaCheck or ZIO Test.
footer: MPL-2.0 Licensed | Copyright © 2022-2025 Haemin Yoo
---

# decrel - Declarative Data Relations in Scala

**decrel** allows you to model relationships between your domain entities and efficiently fetch interconnected data with automatic batching and parallelism.

```scala
// Define your domain model relations
object Post {
  object author extends Relation.Single[Post, Author]
  object comments extends Relation.Many[Post, List, Comment]
}

// Create a complex query
val postWithDetails = Post.author & Post.comments

// Execute the query efficiently
for {
  post <- getPost(postId)
  (author, comments) <- postWithDetails.toZIO(post)
  // Use author and comments...
} yield ()
```

## Why decrel?

- **Solve N+1 Query Problems**: Automatically batch and parallelize data fetches
- **Reuse Access Patterns**: Define relations once, use them anywhere
- **Improve Maintainability**: Keep your domain model and data access cleanly separated
- **Scale Efficiently**: As your application grows, your data access remains optimized

## Quick Links

- [Introduction](/guide/)
- [Getting Started](/guide/getting-started)
- [Defining Relations](/guide/defining-relations)
- [Example Showcase](/showcase/)

## Community

- [GitHub Repository](https://github.com/yoohaemin/decrel)
- [Discussion Forum](https://github.com/yoohaemin/decrel/discussions)

