# ZQuery datasource contracts

Each `implement*Datasource` callback receives deduplicated keys and must return exactly one keyed result for every requested key. Result order is unrestricted. Repeated input positions are reconstructed by ZQuery after fetching a key once.

A missing, duplicate, or unrequested result key is an implementation error. Decrel completes every request in that malformed batch with a `ZQueryDatasourceContractException` defect, whose fields identify the datasource and all three categories of offending keys. No arbitrary duplicate value is selected and no malformed batch is partly accepted.

Optional absence is an explicit `(key, None)` result; a Many relation with no related entities returns `(key, emptyCollection)`. Omitting a key is not treated as absence because it can hide a broken batch implementation.

Domain errors belong in the callback's typed error channel `E`. A batch-wide failure currently fails all requests in that callback together. Existing constructors preserve their error types, including `Nothing`; contract violations are defects rather than an implicit widening of `E`.

## Per-request typed errors

Use `implementSingleDatasourceEither`, `implementOptionalDatasourceEither`,
`implementManyDatasourceEither`, or `implementCustomDatasourceEither` when individual
keys can fail independently. Their callback returns
`ZIO[R, E, Chunk[(In, Either[E, Result])]]`, where `Result` is the relation's complete
output (`Out`, `Option[Out]`, or `CC[Out]`). Return `Right(value)` for success and
`Left(error)` for a domain failure. For Optional relations, `Right(None)` means absence;
it is distinct from a failed request.

A failure of the callback's ZIO itself still fails the entire batch, for example when
the database is unavailable. All constructors enforce the same exact-key contract.

Recover each request before combining queries to collect successes and failures:
`ZQuery.foreachBatched(ids)(id => relation.startingFromQuery(id).either).run`.
Calling `.either` on the combined bulk query instead produces one failure for the
whole result. A failed request also skips its dependent relation steps.

The existing constructors retain their batch-wide error behavior and signatures.
