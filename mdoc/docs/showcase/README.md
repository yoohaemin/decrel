---
lang: en-US
title: decrel showcase
---

# Showcase

This section contains real-world examples showing how Decrel solves common data fetching problems elegantly.

## Basic Example: Blog Post System

Let's consider a simple blog system with posts, authors, and comments. We'll define relations and implement efficient data fetching.

First, our domain model:

```scala
case class Author(id: String, name: String)
case class Post(id: String, title: String, authorId: String, content: String)
case class Comment(id: String, postId: String, authorId: String, content: String)
```

### Defining Relations

```scala
import decrel.Relation

object Author {
  object posts extends Relation.Many[Author, List, Post]
}

object Post {
  object author extends Relation.Single[Post, Author]
  object comments extends Relation.Many[Post, List, Comment]
}

object Comment {
  object author extends Relation.Single[Comment, Author]
  object post extends Relation.Single[Comment, Post]
}
```

### Implementing Data Access with ZQuery

```scala
import decrel.reify.zquery
import zio._

object BlogDataAccess extends zquery[Any] {
  // Simulated data storage
  val authors = Map(
    "a1" -> Author("a1", "Jane Doe"),
    "a2" -> Author("a2", "John Smith")
  )
  
  val posts = List(
    Post("p1", "First Post", "a1", "Hello world!"),
    Post("p2", "Second Post", "a1", "More content"),
    Post("p3", "Another Post", "a2", "Different author")
  )
  
  val comments = List(
    Comment("c1", "p1", "a2", "Great post!"),
    Comment("c2", "p1", "a1", "Thanks!"),
    Comment("c3", "p2", "a2", "Interesting")
  )

  // Implement relations
  implicit val postAuthorProof: Proof.Single[Post.author.type, Post, Nothing, Author] =
    implementSingleDatasource(Post.author) { posts =>
      ZIO.succeed(
        posts.map(post => post -> authors(post.authorId))
      )
    }
    
  implicit val postCommentsProof: Proof.Many[Post.comments.type, Post, Nothing, List, Comment] =
    implementManyDatasource(Post.comments) { posts =>
      ZIO.succeed(
        posts.map(post => 
          post -> comments.filter(_.postId == post.id)
        )
      )
    }
    
  // Other implementations...
}
```

### Using the Relations

```scala
import decrel.syntax.relation._
import zio._

object BlogApp extends ZIOAppDefault {
  def run = {
    // Get a post with its author and all comments
    val postWithDetails = for {
      post <- ZIO.succeed(BlogDataAccess.posts.head)
      details <- (Post.author & Post.comments).toZIO(post)
      (author, comments) = details
      _ <- Console.printLine(s"Post: ${post.title}")
      _ <- Console.printLine(s"By: ${author.name}")
      _ <- Console.printLine(s"Comments: ${comments.size}")
      _ <- ZIO.foreach(comments) { comment =>
        Comment.author.toZIO(comment).flatMap(author =>
          Console.printLine(s"- ${author.name}: ${comment.content}")
        )
      }
    } yield ()
    
    // The magic happens here - all data fetches are batched and run in parallel!
    postWithDetails
  }
}
```

## Advanced Example: E-commerce System

For a more complex example, let's consider an e-commerce system with products, orders, and customers.

### Domain Model with Nested Relations

```scala
import decrel.Relation
import decrel.syntax.relation._

case class Product(id: String, name: String, price: BigDecimal)
case class Customer(id: String, name: String, email: String)
case class Order(id: String, customerId: String, date: java.time.LocalDate)
case class OrderItem(orderId: String, productId: String, quantity: Int)

object Product {
  object orderItems extends Relation.Many[Product, List, OrderItem]
  object orders = orderItems <>: OrderItem.order  // Composed relation
}

object Customer {
  object orders extends Relation.Many[Customer, List, Order]
  object orderItems = orders <>: Order.items  // Nested relation
  object products = orderItems <>: OrderItem.product  // Deep nested relation
}

object Order {
  object customer extends Relation.Single[Order, Customer]
  object items extends Relation.Many[Order, List, OrderItem]
  object products = items <>: OrderItem.product  // Composed relation
}

object OrderItem {
  object order extends Relation.Single[OrderItem, Order]
  object product extends Relation.Single[OrderItem, Product]
}
```

### Complex Query Example

```scala
// Get a customer with all their orders, including all items and product details for each order
val customerOrdersQuery = Customer.orders <>: (Order.items & (Order.items <>: OrderItem.product))

// Using this query
for {
  customer <- getCustomer("c1")
  (orders, orderItemsWithProducts) <- customerOrdersQuery.toZIO(customer)
  // Process the results...
} yield ()
```

## Performance Benefits

In traditional imperative code, fetching all the data in these examples would likely result in N+1 query problems. With Decrel:

1. All queries for the same entity type are automatically batched
2. Independent queries run in parallel
3. Results are automatically joined in memory

For example, if you need to fetch details for 100 posts, each with an author and comments, the traditional approach might make:
- 1 query for posts
- 100 queries for authors (N+1 problem)
- 100 queries for comments (another N+1 problem)

With Decrel, you'd make just:
- 1 query for posts
- 1 batched query for authors
- 1 batched query for comments

## Real-world Applications

Decrel is particularly well-suited for:

1. **GraphQL API implementations** - The declarative nature maps well to GraphQL's hierarchical queries
2. **Microservice architectures** - Efficiently fetch data from multiple services
3. **Legacy system integrations** - Create a clean domain model on top of complex data sources
4. **Testing environments** - Use the same relations with mock generators

These examples demonstrate how Decrel helps you write more declarative, efficient, and maintainable code for data access patterns.
