---
lang: en-US
title: decrel showcase
---

# Showcase

This section contains real-world examples showing how Decrel solves common data fetching problems elegantly.

## Basic Example: Blog Post System

Let's consider a simple blog system with posts, authors, and comments. We'll define relations and implement efficient data fetching.

First, our domain model:

```scala mdoc
case class Author(id: String, name: String)
case class Post(id: String, title: String, authorId: String, content: String)
case class Comment(id: String, postId: String, authorId: String, content: String)
```

### Defining Relations

```scala mdoc
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

```scala mdoc
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
    
  implicit val commentAuthorProof: Proof.Single[Comment.author.type, Comment, Nothing, Author] =
    implementSingleDatasource(Comment.author) { comments =>
      ZIO.succeed(comments.map(comment => comment -> authors(comment.authorId)))
    }
}
```

### Using the Relations

```scala mdoc
import decrel.syntax._
import BlogDataAccess._
import zio._

object BlogApp extends ZIOAppDefault {
  def run: ZIO[Any, java.io.IOException, Unit] = {
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
    
    // Each composed query can batch independent branches. Separate toZIO calls run separately.
    postWithDetails
  }
}
```

## Generating related test data

Supply a generator for the starting value, then use `expand` with relation proofs.

```scala mdoc:reset
import decrel._
import decrel.scalacheck.gen._
import org.scalacheck.Gen
case class Book(id: Int)
object Book {
  object author extends Relation.Single[Book, String]
}
implicit val authorProof: Proof.Single[Book.author.type, Book, String] =
  Gen.relationSingle(Book.author)(book => Gen.const("author-" + book.id))
val bookGen: Gen[Book] = Gen.choose(1, 100).map(Book.apply)
val withAuthor = Relation.Self[Book] & Book.author
val bookWithAuthorGen: Gen[(Book, String)] = bookGen.expand(withAuthor)
```

ZIO Test uses the same `expand` operation with its own generator and proof imports:

```scala mdoc:reset
import decrel._
import decrel.ziotest.gen._
import zio.test.Gen
case class Book(id: Int)
object Book {
  object author extends Relation.Single[Book, String]
}
implicit val authorProof: Proof.Single[Book.author.type, Book, String] =
  Gen.relationSingle(Book.author)(book => Gen.const("author-" + book.id))
val bookGen: Gen[Any, Book] = Gen.int(1, 100).map(Book.apply)
val withAuthor = Relation.Self[Book] & Book.author
val bookWithAuthorGen: Gen[Any, (Book, String)] = bookGen.expand(withAuthor)
```

Generators establish the relationships implemented by these proofs. They do not maintain a shared database of generated entities across arbitrary deeper traversals.
