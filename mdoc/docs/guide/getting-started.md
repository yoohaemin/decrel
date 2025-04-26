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
ivy"com.yoohaemin::decrel-core:@SNAPSHOTVERSION@"       // Defines Relation and derivations
ivy"com.yoohaemin::decrel-zquery:@SNAPSHOTVERSION@"     // Integration with ZQuery
ivy"com.yoohaemin::decrel-fetch:@SNAPSHOTVERSION@"      // Integration with Fetch
ivy"com.yoohaemin::decrel-scalacheck:@SNAPSHOTVERSION@" // Integration with ScalaCheck
ivy"com.yoohaemin::decrel-ziotest:@SNAPSHOTVERSION@"    // Integration with ZIO-Test Gen 
ivy"com.yoohaemin::decrel-cats:@SNAPSHOTVERSION@"       // Integration with F[_]: Monad
```

### Snapshot resolver (sbt)

If you are using an older (< 1.7.0) version of sbt, you might also need to add a resolver.

```scala
resolvers += 
  "Sonatype S01 OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
```

## What module to pick?

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
decrel, and if that datatype has `cats.Monad` implemented.

## Quick Start with ZIO

Here's a minimal example to get you started with decrel and ZIO:

```scala
import decrel.Relation
import decrel.reify.zquery
import decrel.syntax.relation._
import zio._

// 1. Define your domain models
case class User(id: String, name: String)
case class Post(id: String, userId: String, title: String, content: String)

// 2. Define your relations
object User {
  object posts extends Relation.Many[User, List, Post]
}

object Post {
  object user extends Relation.Single[Post, User]
}

// 3. Create your implementation
object DataAccess extends zquery[Any] {
  // Simulated data
  val users = Map(
    "u1" -> User("u1", "Alice"),
    "u2" -> User("u2", "Bob")
  )
  
  val posts = List(
    Post("p1", "u1", "Alice's Post", "Hello!"),
    Post("p2", "u1", "Another Post", "More content"),
    Post("p3", "u2", "Bob's Post", "Hi there")
  )
  
  // Implement the post.user relation
  implicit val postUserProof: Proof.Single[Post.user.type, Post, Nothing, User] =
    implementSingleDatasource(Post.user) { posts =>
      ZIO.succeed(
        posts.map(post => post -> users(post.userId))
      )
    }
    
  // Implement the user.posts relation
  implicit val userPostsProof: Proof.Many[User.posts.type, User, Nothing, List, Post] =
    implementManyDatasource(User.posts) { users =>
      ZIO.succeed(
        users.map(user => 
          user -> posts.filter(_.userId == user.id)
        )
      )
    }
}

// 4. Use in your application
object MyApp extends ZIOAppDefault {
  def run = {
    val program = for {
      post <- ZIO.succeed(DataAccess.posts.head)
      user <- Post.user.toZIO(post)
      _ <- Console.printLine(s"Post '${post.title}' was written by ${user.name}")
      
      // Compose relations - get a user's posts and then get the user of each post (should be the same user)
      user2 <- ZIO.succeed(DataAccess.users("u1"))
      postsWithUsers <- (User.posts <>: Post.user).toZIO(user2)
      _ <- Console.printLine(s"${user2.name} wrote ${postsWithUsers.size} posts")
    } yield ()
    
    program
  }
}
```

## Quick Start with cats-effect

Here's a minimal example using decrel with cats-effect and Fetch:

```scala
import cats.effect._
import decrel.Relation
import decrel.reify.fetch
import decrel.syntax.relation._

// 1. Define your domain models
case class Product(id: String, name: String, price: Double)
case class Order(id: String, customerId: String)
case class OrderItem(orderId: String, productId: String, quantity: Int)

// 2. Define your relations
object Order {
  object items extends Relation.Many[Order, List, OrderItem]
}

object OrderItem {
  object product extends Relation.Single[OrderItem, Product]
}

// 3. Create your implementation
object DataAccess extends fetch[IO] {
  override protected implicit val CF = IO.asyncForIO
  
  // Simulated data
  val products = Map(
    "p1" -> Product("p1", "Laptop", 1200.0),
    "p2" -> Product("p2", "Phone", 800.0),
    "p3" -> Product("p3", "Headphones", 200.0)
  )
  
  val orders = List(
    Order("o1", "c1")
  )
  
  val orderItems = List(
    OrderItem("o1", "p1", 1),
    OrderItem("o1", "p3", 2)
  )
  
  // Implement the orderItem.product relation
  implicit val orderItemProductProof: Proof.Single[OrderItem.product.type, OrderItem, Product] =
    implementSingleDatasource(OrderItem.product) { items =>
      IO.pure(
        items.map(item => item -> products(item.productId))
      )
    }
    
  // Implement the order.items relation
  implicit val orderItemsProof: Proof.Many[Order.items.type, Order, List, OrderItem] =
    implementManyDatasource(Order.items) { orders =>
      IO.pure(
        orders.map(order => 
          order -> orderItems.filter(_.orderId == order.id)
        )
      )
    }
}

// 4. Use in your application
object MyApp extends IOApp.Simple {
  def run: IO[Unit] = {
    val program = for {
      order <- IO.pure(DataAccess.orders.head)
      items <- Order.items.toF(order)(DataAccess.orderItemsProof, IO.asyncForIO.clock)
      _ <- IO.println(s"Order ${order.id} has ${items.size} items")
      
      // Compose relations - get all products in an order
      productsInOrder <- (Order.items <>: OrderItem.product).toF(order)(
        // Implicits are derived automatically
        implicitly, IO.asyncForIO.clock
      )
      _ <- IO.println(s"Products in order: ${productsInOrder.map(_.name).mkString(", ")}")
    } yield ()
    
    program
  }
}
```

## Next Steps

Now that you've set up decrel and seen basic examples, you can:

1. Read [Defining Relations](/guide/defining-relations) to learn how to create a comprehensive domain model
2. Check the [Showcase](/showcase/) for more complex examples
3. Explore advanced topics like caching and optimizing batch fetching