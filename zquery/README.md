# ZQuery datasource contracts

Each `implement*Datasource` callback receives deduplicated keys and must return exactly one keyed result for every requested key. Result order is unrestricted. Repeated input positions are reconstructed by ZQuery after fetching a key once.

A missing, duplicate, or unrequested result key is an implementation error. Decrel completes every request in that malformed batch with a `ZQueryDatasourceContractException` defect, whose fields identify the datasource and all three categories of offending keys. No arbitrary duplicate value is selected and no malformed batch is partly accepted.

Optional absence is an explicit `(key, None)` result; a Many relation with no related entities returns `(key, emptyCollection)`. Omitting a key is not treated as absence because it can hide a broken batch implementation.

Domain errors belong in the callback's typed error channel `E`. A batch-wide failure currently fails all requests in that callback together. Existing constructors preserve their error types, including `Nothing`; contract violations are defects rather than an implicit widening of `E`.
