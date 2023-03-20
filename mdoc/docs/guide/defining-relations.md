---
lang: en-US
title: Defining Relations
---

## What is `Relation`?

A value of type `Relation[A, B]` is a simple declaration that a value of type
`A` is tied to a value of type `B`.

If you imagine your entire domain as a directed graph where nodes are datatypes,
then these relations corresponds to directed edges between those nodes.

## Defining relations

Defining relations is usually done through extending one of existing `decrel.Relation.Declared` 
types with an object.

### Example

```scala mdoc
import decrel.Relation

case class Employee(/* ... */)
object Employee {
  // An Employee object belongs to itself
  object self extends Relation.Self[Employee]

  // An Employee belongs to a single Department
  object department extends Relation.Single[Employee, Department]
  
  // ...
}

case class Department(/* ... */)
object Department {
  // A Department object belongs to itself
  object self extends Relation.Self[Department]
  
  // A Department has a single manager
  object manager extends Relation.Single[Department, Employee]
  
  // A Department has multiple Employees
  object employees extends Relation.Many[Department, List, Employee]
  
  // ...
}
```

### Requirements

- A `Relation` declaration should be done by extending `Relation` types with an `object`. 
  - Specifically, you should not do `val department = new Relation.Single[Employee, Department] {}`.
  - This is because the new type created by declaring an `object`, in this case, `Employee.department.type`,
    is crucial for derivation.

### Recommendations

- Declare relations in the companion object of the datatype that is the starting point of the relation.
    - This makes it easy to reference them when building queries.
- Declare `self` relations!
  - It's convenient to have them when constructing queries.
  - Alternatively, you can use `Relation.Self[A]`, but that's more verbose, and it sticks out when you 
    actually use them.

## Word of advice

decrel's `Relation` type itself does nothing to ensure that the declaration actually make sense 
in the real world, because it *is* supposed the source of truth for your programs. 

Please treat declaring relations as if you are creating other classes to model your domain. You are in 
charge of declaring them, so it's your responsibility to verify what you declared makes sense.

However, if you do happen to declare something completely nonsensical, you would be unable to provide
[`Proof`](/guide/providing-proofs.md)s later, so you won't be able to do any real world operations
with it. 
