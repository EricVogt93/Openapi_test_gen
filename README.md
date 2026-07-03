# oatg — OpenAPI Test Generator

**Kurzüberblick:** `oatg` generiert vollautonom valide HTTP-Requests mit validen
Testdaten direkt aus einer OpenAPI-3.x-Spec — rein schema-getrieben,
deterministisch und komplett ohne AI. Die Requests können sofort gegen eine
laufende API gefeuert werden (Pass/Fail-Report) oder als Artefakte erzeugt
werden: eine Request-Collection (JSON) und Gherkin-Feature-Files, die später
als Basis für Jira-Xray-Tests dienen. Schnittstellen für Jira, GitLab, Xray und
einen optionalen AI-Adapter sind bereits vorbereitet.

---

## What it does

- Parses **OpenAPI 3.0 / 3.1** documents (YAML or JSON), resolving all `$ref`s.
- Generates **schema-valid test data** for every operation: types, formats
  (`email`, `uuid`, `date`, `date-time`, `uri`, `ipv4`, …), `enum`, `pattern`
  (via regex generation), `minLength`/`maxLength`, `minimum`/`maximum`
  (including exclusive bounds), `multipleOf`, `required` vs. optional,
  nested objects, arrays with `minItems`/`maxItems`/`uniqueItems`,
  `allOf`/`oneOf`/`anyOf`, `nullable`, `readOnly` (dropped from request bodies).
  Spec-provided `example`/`default` values always win.
- **Deterministic:** every run has a root seed (printed on start). Same seed ⇒
  byte-identical output. Per-operation child seeds mean adding an endpoint
  doesn't shift the data of the others.
- **Two modes:**
  - `generate` — write artifacts only: `requests.json` (versioned collection)
    and `features/*.feature` (Gherkin, tagged for later Xray import).
  - `run` — fire the requests against a base URL and produce `report.json`
    plus a self-contained `report.html`. Verdict: **PASS** if the response
    status is declared in the spec (exact, `2XX` range, or `default`),
    **FAIL** otherwise, **ERROR** on transport problems.
- **CI-friendly exit codes:** `0` all green, `1` failures/errors, `2` config error.
- Operations the tool can't handle (e.g. multipart-only bodies) are reported
  as **SKIPPED** with a reason — never silently dropped.

## What it does NOT do (yet)

No AI in the core — by design. Not yet supported (see roadmap below):
`multipart/form-data` and form-urlencoded bodies, XML, OAuth flows (use a
static `--auth-bearer` token instead), callbacks/webhooks/links, exotic
parameter styles (`deepObject`, …), negative/boundary testing, stateful
chaining (create → get → delete).

## Quick start

Requires Java 21.

```bash
./gradlew shadowJar
java -jar build/libs/oatg-0.1.0.jar --help

# generate artifacts (collection + Gherkin)
java -jar build/libs/oatg-0.1.0.jar generate \
  --spec openapi.yaml --out oatg-out --seed 42

# fire requests against a running API
java -jar build/libs/oatg-0.1.0.jar run \
  --spec openapi.yaml --base-url http://localhost:8080 \
  --auth-bearer "$TOKEN" --out oatg-out
```

## CLI reference

| Option | Commands | Description |
|---|---|---|
| `--spec <file\|url>` | both | OpenAPI 3.0/3.1 document (required) |
| `--out <dir>` | both | Output directory (default `oatg-out`) |
| `--seed <long>` | both | Root seed; default random, always printed |
| `--include <glob>` / `--exclude <glob>` | both | Path filters, repeatable (e.g. `--include '/pets/**'`) |
| `--include-methods GET,POST` | both | HTTP method filter |
| `--auth-bearer <token>` | both | `Authorization: Bearer …` |
| `--auth-basic <user:pass>` | both | Basic auth |
| `--auth-apikey NAME=VALUE[:header\|query\|cookie]` | both | API key; location defaults to the spec's securityScheme |
| `--header 'K: V'` | both | Extra static header, repeatable |
| `--optional-props always\|never\|random` | both | Optional property handling (default `always`) |
| `--max-depth <n>` | both | Recursion cap for nested schemas (default 5) |
| `--formats collection,gherkin` | generate | Which artifacts to write |
| `--gherkin-group tag\|path` | generate | Feature file grouping (default `tag`) |
| `--base-url <url>` | both | Target API (run) / recorded in collection (generate); falls back to the spec's first server |
| `--timeout <seconds>` | run | Per-request timeout (default 10) |
| `--concurrency <n>` | run | Parallel requests (default 1) |
| `--validate-response` | run | Also check content type + JSON well-formedness |
| `--fail-on-5xx` | run | Any 5xx fails, even when declared |
| `--dry-run` | run | Assemble and print without sending |
| `--report json,html` | run | Report formats |

## How data generation works

Value precedence per schema: `example`/`examples` → `default` → `enum`
(seeded pick) → `const` → `format` handler → `pattern` (regex generation) →
type-based constrained random. `allOf` is merged; for `oneOf`/`anyOf` one
branch is picked deterministically. Recursive schemas are cut off at
`--max-depth`. Realistic values for formats like `email` come from a seeded
[Datafaker](https://www.datafaker.net/) — still fully deterministic.

## Output formats

- **`requests.json`** — versioned collection (`"collectionVersion": 1`):
  spec info, root seed, base URL and one entry per request with method,
  path template, resolved path, query/header/cookie params, JSON body,
  expected statuses and the per-operation child seed. Easy to transform
  into Postman/Bruno collections downstream.
- **`features/*.feature`** — one feature per tag (or path segment), one
  scenario per operation. Scenarios are tagged
  `@generated @seed-<rootSeed> @operation-<id>` so a later Xray import can
  key on them.
- **`report.json` / `report.html`** — run metadata (spec, base URL, seed,
  timestamp), totals and one entry per operation (verdict, status, latency,
  reason). SKIPPED operations are included.

## CI usage

GitHub Actions builds this repo (`.github/workflows/ci.yml`). For the team's
GitLab, see [`docs/gitlab-ci-example.yml`](docs/gitlab-ci-example.yml) — build
the fat JAR once, then run `oatg run` against the deployed service and fail
the pipeline on exit code 1.

## Extension points (roadmap)

The core only talks to four small interfaces in `io.oatg.spi`
(no-op implementations are wired by default, see `docs/architecture.md`):

| SPI | Planned adapter |
|---|---|
| `TestDataEnhancer` | Optional local AI (e.g. Qwen via an OpenAI-compatible endpoint) to make test data more realistic — the core stays AI-free |
| `TicketSource` | Jira: pull tickets and read Gherkin acceptance criteria |
| `CommitAnalyzer` | GitLab: detect which endpoints a merge request touched |
| `TestManagementPublisher` | Jira Xray: create/update test issues from the generated scenarios |

## Development

```bash
./gradlew build       # compile + all tests (unit + WireMock integration)
./gradlew shadowJar   # fat JAR at build/libs/oatg-0.1.0.jar
```

Test specs live in `src/test/resources/specs/` — `petstore.yaml` for the
end-to-end flow, `tricky-schemas.yaml` for the generator's edge cases.
