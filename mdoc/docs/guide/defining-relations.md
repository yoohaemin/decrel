---
lang: en-US
title: Defining relations
---

# Defining relations

A relation declares a domain relationship independently of its interpreter. `Single` returns exactly one value, `Optional` returns an `Option`, and `Many` returns a collection.

```scala mdoc
import decrel._

case class Employee(id: String)
object Employee {
  object department extends Relation.Single[Employee, Department]
  object manager extends Relation.Optional[Employee, Employee]
}
case class Manager(id: String)
case class Department(id: String)
object Department {
  object manager extends Relation.Single[Department, Manager]
  object employees extends Relation.Many[Department, List, Employee]
}

```

## Follow, retain, and zip

`>>:` follows the next relation and discards the intermediate value. `<>:` follows it and retains the intermediate value alongside the result. Both operators associate to the right. `&` (also named `zip`) combines independent relations from the same input; the interpreter determines their execution strategy.

```scala mdoc
val departmentManager = Employee.department >>: Department.manager
val departmentAndManager = Employee.department <>: Department.manager
val departmentInfo = Department.manager.zip(Department.employees)
val employeeDepartmentInfo = Employee.department <>: departmentInfo

val managerResult: Relation[Employee, Manager] = departmentManager
val retainedResult: Relation[Employee, (Department, Manager)] = departmentAndManager
```

## Implement and execute

Import the interpreter instance to bring its proofs and execution syntax into scope.

```scala mdoc
import decrel.reify.zquery
import zio.ZIO

object EmployeeRelations extends zquery[Any] {
  implicit val departmentProof: Proof.Single[Employee.department.type, Employee, Nothing, Department] =
    implementSingleDatasource(Employee.department) { employees =>
      ZIO.succeed(employees.map(e => e -> Department("dept-" + e.id)))
    }
  implicit val managerProof: Proof.Single[Department.manager.type, Department, Nothing, Manager] =
    implementSingleDatasource(Department.manager) { departments =>
      ZIO.succeed(departments.map(d => d -> Manager("manager-" + d.id)))
    }
}
import EmployeeRelations._
val result: ZIO[Any, Nothing, Manager] =
  departmentManager.toZIO(Employee("alice"))
```

## Adapt a proof

`contramap` adapts an existing proof to a declared relation with a different input.

```scala mdoc
object departmentByEmployeeId extends Relation.Single[String, Department]
val byIdProof = departmentProof.contramap(departmentByEmployeeId)(Employee.apply)
val byIdResult = byIdProof.reify.apply("alice")
```

## Custom interpretation

Wrap a relation with `customImpl` to provide a specialized proof for the whole expression. The wrapper retains the original relation's input and output types.

```scala mdoc
val customRelation = departmentAndManager.customImpl
val customShape: Relation[Employee, (Department, Manager)] = customRelation
```

See [Getting started](./getting-started.md) for complete ZQuery and Fetch examples.
