# kcrdt

Conflict-free replicated data types (CRDTs) for Kotlin/JVM, with no runtime
dependencies beyond the Kotlin standard library.

> Work in progress: a collaborative-text sequence CRDT and a convergence
> simulator are coming next.

## What's here so far

| Type | Kind | Notes |
|---|---|---|
| `VectorClock` | causality tracking | `compare` returns `BEFORE` / `AFTER` / `EQUAL` / `CONCURRENT` |
| `HybridLogicalClock` / `Hlc` | timestamps | physical ms + logical counter + replica tie-break; drift guard |
| `GCounter` | state CRDT | grow-only counter |
| `PNCounter` | state CRDT | increment/decrement counter |
| `LwwRegister` | state CRDT | last-writer-wins by HLC timestamp |
| `MvRegister` | state CRDT | keeps concurrent writes as siblings |
| `DotContext` | causal context | version vector + dot cloud, auto-compacted |
| `OrSet` | state CRDT | add-wins observed-remove set, no per-element tombstones |
| `LwwMap` | state CRDT | per-key LWW registers with timestamped tombstones |

Every state CRDT implements `StateCrdt<T>.merge`, and the test suite checks the
join-semilattice laws (commutative, associative, idempotent) over hundreds of
randomly generated replica histories.

## Build and test

Requires JDK 21 and Maven 3.9+.

```sh
mvn test
```

## License

MIT
