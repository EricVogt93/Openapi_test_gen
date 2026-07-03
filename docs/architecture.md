# Architecture

## Data flow

```
spec file ─► SpecLoader (swagger-parser, $refs fully resolved)
          ─► OperationExtractor → EndpointOperation[] (+ SkippedOperation[])
          ─► DataGenerator (seeded, schema-driven)      ┐
          ─► TestDataEnhancer.enhance(…)  (SPI, no-op)  │ GeneratedRequest[]
          ─► RequestAssembler (+ AuthConfigurer)        ┘
              ├─ run:      ExecutionEngine → ResponseValidator → ReportModel → Json/HtmlReportWriter
              └─ generate: RequestCollectionWriter + GherkinWriter
```

`core/GenerationService` implements the shared pipeline; the picocli commands
in `cli/` only parse options, call the service and hand the result to writers.

## Packages

| Package | Responsibility |
|---|---|
| `spec` | Load + validate the OpenAPI document, flatten paths into operations |
| `gen` | Deterministic schema-driven data generation (the heart of the tool) |
| `request` | Turn operations + generated values into concrete requests; static auth |
| `exec` | Fire requests (JDK HttpClient), decide PASS/FAIL/ERROR |
| `report` | JSON + self-contained HTML report |
| `artifact` | Request-collection JSON and Gherkin feature files |
| `spi` | Extension interfaces + no-op defaults (Jira/GitLab/Xray/AI adapters plug in here) |
| `core` | Shared pipeline and resolved configuration |
| `cli` | picocli commands `generate` and `run` |

## Determinism

- One **root seed** per run (`--seed`, printed on start).
- Each operation gets a **child seed** derived from
  `rootSeed ⊕ stableHash(method + " " + path)` — see
  `GenerationService.childSeed`. Adding or removing one endpoint therefore
  never shifts the generated data of the others.
- All value sources (RgxGen for patterns, Datafaker for realistic formats,
  plain `Random` elsewhere) draw from the per-operation seeded `Random`.

## Design rules

- The core packages (`gen`, `request`, `exec`) may **call** the SPI interfaces
  but never depend on concrete implementations.
- Wiring happens in one place (`Extensions.defaults()`, used by the commands);
  a future version can switch to `ServiceLoader` discovery without touching
  the interfaces.
- Anything the tool cannot handle is surfaced (SKIPPED entries, warnings) —
  no silent drops.

## Adding an adapter (example: Xray)

1. Implement `io.oatg.spi.TestManagementPublisher` (new module or package
   with its own HTTP client / credentials handling).
2. Replace the corresponding no-op in the `Extensions` construction.
3. The `generate` command already passes every generated scenario (with
   `@operation-…` and `@seed-…` tags) to the publisher.

The same pattern applies to `TicketSource` (Jira), `CommitAnalyzer` (GitLab)
and `TestDataEnhancer` (optional local AI via an OpenAI-compatible endpoint —
the enhancer receives the schema context plus the valid candidate value and
must return a schema-valid replacement).
