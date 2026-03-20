---
lang: en-US
title: Advanced APIs
---

# Advanced APIs

Most users only need declared relations, composition, and `implement*Datasource`.

This page covers the public APIs that matter when you need more control.

## `Proof`

`Proof` is the runtime evidence that a relation can be reified into an executable access function.

Use it directly when:

- helper constructors are not enough
- you are building your own integration
- you want to understand how implicit derivation is guided

## `contramap*Proof`

These helpers are usually the first advanced API to reach for.

They let you:

- reuse identifier-based proofs
- separate canonical fetch logic from richer domain inputs
- avoid duplicating datasource code

## `Cache`

`Cache` is intentionally explicit.

Use it to:

- seed already-known data
- control cache behavior in tests
- bridge cached values from an upstream layer

## `Relation.Custom` and `customImpl`

These are the right tools when a composed traversal should become a stable, named relation with its own proof.

## `catsMonad`, `kyoGeneric`, and module traits

These APIs are for custom integrations and framework authors more than application users.

Read them as extension surfaces:

- `decrel.reify.catsMonad`
- `decrel.reify.kyoGeneric`
- `decrel.reify.monofunctor.module`
- `decrel.reify.bifunctor.module`

They are public because decrel is not limited to one effect system.
