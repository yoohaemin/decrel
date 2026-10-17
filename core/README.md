# Relation proof identity

The relation parameter of `Proof` and its cardinality-specific subclasses is invariant. A proof witnesses one particular relation tree, not an arbitrary supertype of that tree. Input and error variance are unchanged.

Covariant relation witnesses allowed implicit search to select a `Self` proof with an inferred bottom relation type, then widen it to an unrelated relation. A composed path returning to its input type could therefore silently become identity and skip every datasource. Invariance prevents this substitution; a missing proof now produces a compilation error.

Keep the inferred AST type of reusable relation expressions. If a nested inline expression no longer resolves during proof derivation, first assign the composed expression to a `val`, then call `reify` or `startingFrom` on that value. Do not widen it to the general `Relation[In, Out]` type: that discards the structure needed to derive its proof.

This is a source-level restriction for code that previously widened relation witnesses. Explicit `Relation.Self` values, singleton Self declarations, and proofs registered for actual domain relations remain supported.
