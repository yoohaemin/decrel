---
lang: en-US
title: Bulk collection contract
---

# Bulk collection contract

Bulk inputs and the collection in `Relation.Many` proofs must be finite, ordered immutable sequences. Use `List` or `Vector`; convert arrays and sets before traversal. An infinite lazy sequence cannot complete a bulk query.

The bulk API now requires `Seq` and `SeqOps` rather than `Iterable` and `IterableOps`. This is a source-breaking restriction for custom interpreter implementations and unordered collection callers. Update overridden `applyMultiple` signatures to the new bounds, and convert unsupported inputs explicitly.

For every bulk call, an interpreter must return exactly one output per input, preserving input order and duplicate positions. Optional composition leaves `None` in its original position. Many composition preserves every inner group's boundaries, including empty groups and equal outputs. Deduplicating backend requests is allowed as long as results are expanded back to these positions.

These laws let composition reconstruct nested results safely. `Set.map` can merge equal outputs, so sets cannot satisfy the contract and are rejected by the public bulk API. The sequence bound does not prove finiteness or that a custom implementation obeys the result contract; these remain interpreter obligations.
