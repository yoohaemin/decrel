---
lang: en-US
title: Defining relations
---

# Defining relations



Please note that `Relation` does nothing to ensure that the declaration is "true", and there
is nothing stopping you from declaring something nonsensical like `Relation[Unit, Nothing]`.
For that, you would need [`Proof`](#proof)s.



### Example

```scala mdoc
import decrel.Relation

case class Employee(/* ... */)
object Employee {
  // An Employee belongs to a single Department
  case object department extends Relation.Single[Employee, Department]
  
  // ...
}

case class Department(/* ... */)
object Department {
  // A Department has a single manager
  case object manager extends Relation.Single[Department, Employee]
  
  // A Department has multiple Employees
  case object employees extends Relation.Many[Department, List, Employee]
  
  // ...
}
```