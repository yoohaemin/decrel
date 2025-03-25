---
lang: en-US
title: Defining relations
---

# Defining Relations

Relations are the core concept in Decrel. They describe how different entities in your domain model connect to each other. This guide explains how to define and use relations effectively.

## Basic Relation Types

Decrel provides three primary types of relations:

### 1. Single Relations (`Relation.Single`)

Use this when one entity relates to exactly one other entity.

```scala
import decrel.Relation

// An employee has exactly one department
object Employee {
  object department extends Relation.Single[Employee, Department]
}
```

### 2. Optional Relations (`Relation.Optional`)

Use this when an entity may or may not relate to another entity.

```scala
import decrel.Relation

// An employee may have a manager (or not)
object Employee {
  object manager extends Relation.Optional[Employee, Employee]
}
```

### 3. Many Relations (`Relation.Many`)

Use this when one entity relates to multiple entities of the same type.

```scala
import decrel.Relation

// A department has multiple employees
object Department {
  object employees extends Relation.Many[Department, List, Employee]
}
```

## Composing Relations

Relations can be combined to express complex access patterns:

### Sequential Composition (`<>:`)

To follow one relation and then another:

```scala
import decrel.syntax.relation._

// Get the manager of an employee's department
val departmentManager = Employee.department <>: Department.manager
```

### Parallel Composition (`&`)

To retrieve multiple related entities at once:

```scala
import decrel.syntax.relation._

// Get both the manager and employees of a department
val departmentInfo = Department.manager & Department.employees
```

### Combined Composition

You can combine these operators to create complex queries:

```scala
import decrel.syntax.relation._

// Get the department of an employee, along with that department's manager 
// and all employees in that department
val employeeDepartmentInfo = Employee.department <>: (Department.manager & Department.employees)
```

## Implementing Relations

To use relations for data access, you need to implement them based on your data source:

### With ZQuery

```scala
import decrel.reify.zquery
import zio._

// Create a module with your implementation
object EmployeeRelations extends zquery[Any] {
  
  implicit val employeeDepartmentProof: Proof.Single[Employee.department.type, Employee, Nothing, Department] =
    implementSingleDatasource(Employee.department) { employeeIds =>
      // Implementation to fetch departments for employees
      ZIO.succeed(
        employeeIds.map(id => id -> Department(s"Dept-${id.value}"))
      )
    }

  // Other relation implementations...
}
```

### With Fetch (cats-effect)

```scala
import decrel.reify.fetch
import cats.effect.IO

// Create a module with your implementation
object EmployeeRelations extends fetch[IO] {
  
  implicit val employeeDepartmentProof: Proof.Single[Employee.department.type, Employee, Department] =
    implementSingleDatasource(Employee.department) { employeeIds =>
      // Implementation to fetch departments for employees
      IO.pure(
        employeeIds.map(id => id -> Department(s"Dept-${id.value}"))
      )
    }

  // Other relation implementations...
}
```

## Advanced Patterns

### Contramap

Sometimes you need to adapt existing relations to work with different input types:

```scala
import decrel.Relation

// Existing relation
object Department {
  object manager extends Relation.Single[Department, Employee]
}

// Adapt to work with department ID instead of department object
val departmentIdToManager = contramapOneProof(
  departmentManagerProof,
  Department.manager,
  (id: Department.Id) => Department(id)
)
```

### Custom Relations

For specialized cases, you can create custom relations:

```scala
import decrel.Relation

val customRelation = Relation.Custom(
  // Reference to existing relation or custom implementation
)
```

## Best Practices

1. **Domain-Driven Design**: Define relations that match your domain model's natural language
2. **Composition Over Implementation**: Focus on composing relations first, implement later
3. **Batch Fetching**: Always implement batch fetching to avoid N+1 problems
4. **Single Responsibility**: Each relation should represent one clear relationship

## Next Steps

After defining your relations, you'll want to:

1. Implement data access using ZQuery or Fetch 
2. Use your relations in your application code
3. Consider adding automated testing with ScalaCheck or ZIO Test
