---
lang: en-US
title: Introduction
---

# Hey there :)

Welcome to the documentation of **decrel**!

I appreciate your interest in the library, and I hope you will learn what you want to know on these pages.

If something is unclear, or if you have a suggestion, please don't hesitate to point it out in the
[discussions section](https://github.com/yoohaemin/decrel/discussions) in Github, or [email me](mailto:haemin2142@zzz.pe.kr).

## Objective

The implicit knowledge of **relation**s, usually present in the domain of an application,
are rarely directly used when building applications in Scala.

In fact, in my experience, they are rarely even documented.

decrel aims to change the status quo; it is a library that allows the definition and utilization of relations between data.

## What is it suitable for?

You will be able to create a value that represents something like:

- I have an Employee object
- Give me the *Department* object of that employee
    - Also give me the *Employee* object of the *manager* of that department
    - Also give me the *list of Employees* of that department

For this imaginary example, the expression would look something like this:

```scala
val query = Employee.department <>: (Department.manager & Department.employees)
```

Now, this query can exist in two completely different contexts, namely:
- Fetching data from a datasource
- Generating mock data

The above query can be used in both contexts, unmodified!

## Anything else?

I gave a talk at the Functional Scala 2022 conference.

Due to time constraints, the talk is mostly focused on the motivation, and is rather light on the specific details.

You can watch it here:

<iframe width="560" height="315"
src="https://www.youtube.com/embed/kcYgrYIbHM0"
frameborder="0"
allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
allowfullscreen></iframe>

Everything I said is still relevant, except for two details:
- `decrel-core` now also depends on `izumi-reflect,` apart from Scala stdlib
- `:>:` was renamed to `<>:` ([Reason](https://github.com/yoohaemin/decrel/pull/37))
