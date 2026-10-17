---
lang: en-US
title: Introduction & Motivation
---

# Introduction & Motivation

**decrel** stands for **dec**larative programming using **rel**ations. It is a typed relation algebra with effect-specific interpreters.

## The cost of imperative fetching

A typical service loads a book, then its author, then its price. The last two calls are independent, but sequential `flatMap` composition waits for one before starting the other. Repeating that program for 50 books can issue 150 calls if the repositories do not batch or cache them.

Manually introducing batch APIs requires collecting keys, fetching maps, and joining results back to inputs. Tests often repeat that relationship knowledge in separate generators.

| Concern | Decrel representation |
| --- | --- |
| Domain edge | `Single`, `Optional`, or `Many` declaration |
| Graph traversal | `>>:`, `<>:`, and `&` expressions |
| Executable behavior | `Proof` and `ReifiedRelation` |
| Execution strategy | ZQuery, Fetch, a generator, or another effect |

## One relation graph, different interpreters

```scala mdoc:silent
import decrel.*
case class Employee(id: String)
case class Department(id: String)
object Employee {
  case object department extends Relation.Single[Employee, Department]
}
object Department {
  case object manager extends Relation.Single[Department, Employee]
  case object employees extends Relation.Many[Department, List, Employee]
}
val query = Employee.department <>: (Department.manager & Department.employees)
val shape: Relation[Employee, (Department, Employee, List[Employee])] = query
```

The expression contains no database calls. With runtime proofs it can fetch data; with generator proofs it can generate values. Batching, caching, and concurrency depend on the interpreter and datasource implementations, rather than following from the relation type alone.

## Conference talk

Haemin Yoo discussed the motivation at Functional Scala 2022:

<iframe width="560" height="315" src="https://www.youtube.com/embed/kcYgrYIbHM0" title="Decrel at Functional Scala 2022" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>

Since the talk, the core gained its izumi-reflect dependency and `:>:` was renamed to `<>:`. Continue with [Getting started](./getting-started.md).
