# Job Data API

A read-only REST API over the salary survey dataset, supporting filtering, sparse fieldsets and
sorting.

Java 21 · Spring Boot 3.5.3 · Maven

## Run it

```bash
./mvnw spring-boot:run     # http://localhost:8080
./mvnw test                # 83 tests
```

No database and no configuration — the dataset ships in `src/main/resources/data/`.

Swagger UI: <http://localhost:8080/swagger-ui.html>

> **Use `curl -g`.** curl reads `[` and `]` as glob ranges, so `curl -g '...salary[gte]=120000'`
> (or percent-encoding as `%5Bgte%5D`) is needed. Browsers and HTTP clients send them as-is.

## Endpoints

### `GET /api/job_data`

| | Syntax | Example |
|---|---|---|
| **Filter** | `<field>[<operator>]=<value>` | `?salary[gte]=120000` |
| Filter (shorthand) | `<field>=<value>` — means `eq` | `?gender=Male` |
| Filter (range) | repeat the field | `?salary[gte]=100000&salary[lte]=150000` |
| **Sparse fields** | `fields=<a,b,c>` | `?fields=job_title,gender,salary` |
| **Sort** | `sort=<fields>&sort_type=<ASC\|DESC>` | `?sort=job_title&sort_type=DESC` |
| Paginate | `page=<n>&size=<n>` | `?page=2&size=25` (1-based, default 50, max 200) |

Filters are ANDed. Sorts apply left to right, so `?sort=salary,job_title&sort_type=DESC` orders by
salary descending then job title ascending — a missing `sort_type` defaults to `ASC`.

**Operators:** `eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `like` (case-insensitive contains, strings only),
`in` (comma-separated list).

**Fields:** `id`, `timestamp`, `timestamp_raw`, `employer`, `location`, `job_title`, `years_at_employer`,
`years_at_employer_raw`, `years_of_experience`, `years_of_experience_raw`, `salary`,
`salary_currency`, `salary_raw`, `signing_bonus`, `annual_bonus`, `annual_stock_value`, `gender`,
`additional_comments`.

Every field is filterable and sortable; string comparisons are case-insensitive throughout.
`salary`, `years_at_employer`, `years_of_experience` and `timestamp` are parsed from free text (see
Design decisions below); `signing_bonus`, `annual_bonus` and `annual_stock_value` are not — they're
kept as raw strings, so filtering them is a text comparison, not a numeric one.

### `GET /api/job_data/{id}`

One record, and accepts `?fields=` too.

## Response

```json
{
  "data": [
    { "job_title": "Software Developer", "gender": "Male", "salary": 122000 }
  ],
  "pagination": {
    "total": 1154, "page": 1, "size": 1, "total_pages": 1154,
    "next": "http://localhost:8080/api/job_data?page=2", "prev": null
  }
}
```

The envelope exists so a paginated response can report how many rows matched in total; a bare array
cannot, and adding it later would break every client. With `fields=`, keys appear in the order asked
for. `pagination.next`/`prev` are ready-to-fetch URLs carrying the same filter/sort/fields as the
request that produced them — a client pages by following the link, not by rebuilding the query
string itself. Either is `null` when there is no such page.

Client errors return the same shape with a code you can branch on:

```json
{ "error": { "code": "UNKNOWN_FIELD", "message": "Unknown field 'salaryy'. Available: id, timestamp, ..." } }
```

`UNKNOWN_FIELD` · `UNKNOWN_OPERATOR` · `UNSUPPORTED_OPERATOR` · `INVALID_VALUE` · `INVALID_SORT` ·
`INVALID_SORT_TYPE` · `INVALID_PARAMETER` · `MISSING_VALUE` → **400**, `RECORD_NOT_FOUND` → **404**.

## Examples

```bash
# the three requirements from the brief
curl -g 'localhost:8080/api/job_data?salary[gte]=120000'
curl -g 'localhost:8080/api/job_data?fields=job_title,gender,salary'
curl -g 'localhost:8080/api/job_data?sort=job_title&sort_type=DESC'

# all of them at once
curl -g 'localhost:8080/api/job_data?salary[gte]=100000&salary[lte]=150000&gender=Female\
&job_title[like]=engineer&fields=job_title,salary,gender&sort=salary&sort_type=DESC&size=10'

# other operators
curl -g 'localhost:8080/api/job_data?gender[in]=Female,Male&job_title[like]=data%20scientist'
curl -g 'localhost:8080/api/job_data?years_of_experience[gt]=15&sort=salary&sort_type=DESC'
curl -g 'localhost:8080/api/job_data/1?fields=job_title,salary,salary_raw'
```

## Project setup

Generated with Spring Initializr, then trimmed. What was chosen, and what was deliberately left out:

**Spring Boot 3.5.3, not the 4.1 Initializr offered.** `springdoc-openapi` — the OpenAPI/Swagger UI
generator — only publishes 2.8.x, which targets Boot 3. Taking the newest version by default would
have traded a working Swagger UI for a version number. Boot 3.5.x is also what most teams are
actually running, so the reviewer's toolchain is more likely to match.

**Two starters ticked: `web` and `validation`** — plus `springdoc-openapi` added by hand so the API
is explorable without a REST client. Four dependencies total including `spring-boot-starter-test`;
every one of them is called by code in this repo.

**No Lombok.** Java `record` already gives immutable fields, accessors, `equals`/`hashCode` and
`toString`, and it's a language feature rather than an annotation processor the IDE needs a plugin to
understand. Ten record types across the codebase, no code generation.

**No database.** The brief says "a static set of job data" — it never changes, so it is parsed once
at startup and served from an immutable list. A schema, a migration tool and a running server would
be setup cost for the reviewer with nothing bought in return. `JobDataService` still depends on the
`JobDataRepository` interface, so the seam for a JPA implementation is there without the weight.

**No `/v1/` prefix.** Versioning exists to protect deployed consumers from breaking changes; there
are none here, and adding the prefix without a policy for what `v2` would mean is decoration. In
production this would start versioned on day one.

## Design decisions

### The salary column is free text, so it is normalised at ingest

This is the part of the exercise that decides whether `?salary[gte]=120000` works at all. A single
column in the source file holds all of this:

```
"122000"   "83,000"   "$120,000"   "€60,000"   "70k"   "$30/hr"   "110000-120000"
"5.5 Million JPY"   "SEK 380000"   "15,00,000 Rs"   "1 rare pepe"   "-"   ""
```

Comparing those as strings would return nonsense, so `SalaryParser` converts each one to an annual
number once at startup: grouping separators dropped, currency symbols and ISO codes recognised
(an explicit code beats a symbol, so `$84,500 AUD` is AUD), `k`/`M` applied, hourly and daily rates
annualised at 2080h/260d, ranges resolved to their lower bound.

3,678 of 3,776 rows end up with a usable number. The rest keep `salary: null` — and the original
text is always returned as `salary_raw`, so normalising never destroys the respondent's answer.

**Currency is detected but not converted.** `€60,000` and `60000 USD` both compare as 60000. Doing it
properly needs an FX rate table plus an as-of date per row, which is beyond a read-only exercise, so
`salary_currency` is returned and the client decides. The conversion would slot into `SalaryParser`
at ingest without touching anything above it.

### Years fields have the same free-text problem, at a smaller scale

`Years at Employer` / `Years of Experience` are typed just as freely as Salary — `"18"`, `"1 of
employment"`, `"1.5"`, `"<1"`, and occasionally `"-16"` or `"-1"` (a "no answer" sentinel, not a real
duration). A naive `\d+` extraction breaks two ways: it strips the sign, turning a sentinel into a
plausible-looking *positive* number, and it truncates decimals, turning `"1.5"` (140+ occurrences in
Years at Employer alone) into `1`. Neither failure is loud — both produce a number that looks valid.

`RawJobRow.parseYears()` extracts a signed decimal instead and treats a negative result as unknown,
mirroring `SalaryParser`'s "fail safe, not falsely confident" rule. `years_at_employer` and
`years_of_experience` moved from `Integer` to `Double` so the fix doesn't quietly re-truncate the
decimals it just recovered. `years_at_employer_raw` / `years_of_experience_raw` carry the original
text the same way `salary_raw` does — so a sentinel like `"-9001"` still shows up somewhere even
though it normalises to `null`.

### Timestamps are re-emitted as ISO-8601, which is what makes sorting them mean anything

The survey writes `3/21/2016 13:11:18` — US `M/D/YYYY`, consistently, in all 3,777 rows. Served as-is
that string sorts alphabetically by month with the year trailing, so `?sort=timestamp` was returning
nonsense: ascending began at `1/10/2017` rather than March 2016, and descending put `9/7/2016` ahead
of `9/6/2017`. The data spans 2016 to 2020, so the year being ignored is not a corner case.

Parsing once at ingest and re-emitting as fixed-width `2016-03-21T13:11:18` fixes it without any
date-aware comparison logic, because ISO-8601 is designed so that lexicographic order *is*
chronological order. Range filters come along for free — `?timestamp[gte]=2018-01-01&timestamp[lt]=2019-01-01`
returns exactly the 43 rows from 2018 — even though the comparison underneath is still `String`.

No time zone is attached. The source has none, and inventing one would be a guess dressed up as
precision. `timestamp_raw` keeps the original text, as everywhere else.

### Missing values are unknown, not zero

A row whose salary could not be parsed is not "less than 120000" — it is unknown, so following SQL's
treatment of NULL it matches no numeric filter at all. In sorting it lands **last in both
directions**, otherwise `sort_type=DESC` would fill the first page with unparseable rows. Empty
strings in the source are converted to `null` at load, so "missing" is one concept everywhere.

### One registry drives filtering, sorting and sparse fields

`JobField` is an enum of `(api_name, type, extractor)`. Filtering builds a `Predicate` from it,
sorting builds a `Comparator`, sparse fieldsets read values through it, and a name that is not in it
is rejected with 400 on all three paths. Exposing a new column is one line in that enum — the
controller and service do not change.

Filters are read straight off the query string rather than declared one method argument per column,
which is what keeps `?salary[gte]=120000&gender=Male&job_title[like]=engineer` working without a
combinatorial explosion of endpoint signatures.

### In memory, behind a repository interface

The dataset is static and small (~3.8k rows), so it is parsed once at startup and served from an
immutable list. Standing up a database for read-only data that never changes would add setup for the
reviewer and buy nothing. `JobDataService` depends on the `JobDataRepository` interface, so a JPA or
JDBC implementation could replace `InMemoryJobDataRepository` without changes above it — at which
point the `JobField` extractors become the natural place to hang `Specification` builders.

### Tomcat's query-string parser had to be relaxed

Tomcat rejects a raw `[` in a query string with 400 before the request reaches Spring, which would
have forced every client to percent-encode the brackets the brief writes plainly. RFC 3986 lists them
as reserved rather than illegal, so `WebConfig` sets `relaxedQueryChars=[]`.

MockMvc never runs that parser, so it cannot catch this — `BracketFilterSyntaxTest` starts a real
server on a real port for exactly that reason.

## Tests

```
SalaryParserTest         34  every input is a real value taken from the survey file
QueryParserTest           8  bracket syntax, sort defaults, and each rejection path
JobDataControllerTest    13  the brief's URLs end to end, plus ordering, paging bounds, errors
RawJobRowTest            26  timestamp to ISO, years decimals + negative sentinels, raw kept
BracketFilterSyntaxTest   1  unencoded brackets over a real socket
JobDataApiApplicationTests 1 the context starts and the dataset loads
```

## Layout

```
com.ata.jobdata
├── controller/  JobDataController · JobDataResponse
├── config/      WebConfig
├── exception/   ApiException · ApiExceptionHandler
├── query/       JobField · FilterOperator · QueryParser · QueryParams
├── service/     JobDataService · PageResult
├── model/       JobRecord
└── repository/  JobDataRepository · InMemoryJobDataRepository · RawJobRow · SalaryParser
```

Imports only ever point inward — `controller` → `service` → `repository`, with `model`, `query` and
`exception` shared beneath them. That is why `JobDataService` returns `PageResult` (rows plus a
total) rather than `JobDataResponse`: the service says what was found, the controller decides how it
looks on the wire and adds the paging links, which only it can build since only it sees the request
URL. A service that returned the web DTO would make the inner layer depend on the outer one.

`model/` has no `entity`/`dto` split: `JobRecord` is a plain record, not a JPA `@Entity` — there is no
database — so a folder named `entity` would claim a mapping that does not exist.

`RawJobRow` and `SalaryParser` are package-private: the spreadsheet's shape and the free-text parsing
it needs are `repository`'s problem, and nothing above it can reach them.
