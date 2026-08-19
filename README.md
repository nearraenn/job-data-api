# Job Data API

A read-only REST API over the salary survey dataset, supporting filtering, sparse fieldsets and
sorting.

Java 21 · Spring Boot 3.5.3 · Maven

## Run it

```bash
./mvnw spring-boot:run     # http://localhost:8080
./mvnw test                # 65 tests
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

**Fields:** `id`, `timestamp`, `employer`, `location`, `job_title`, `years_at_employer`,
`years_of_experience`, `salary`, `salary_currency`, `salary_raw`, `signing_bonus`, `annual_bonus`,
`annual_stock_value`, `gender`, `additional_comments`.

Every field is filterable and sortable; string comparisons are case-insensitive throughout.
`salary`, `years_at_employer` and `years_of_experience` are parsed from free text (see Design
decisions below); `signing_bonus`, `annual_bonus` and `annual_stock_value` are not — they're kept as
raw strings, so filtering them is a text comparison, not a numeric one.

### `GET /api/job_data/{id}`

One record, and accepts `?fields=` too.

## Response

```json
{
  "data": [
    { "job_title": "Software Developer", "gender": "Male", "salary": 122000 }
  ],
  "meta": { "total": 1154, "page": 1, "size": 1, "total_pages": 1154 }
}
```

The envelope exists so a paginated response can report how many rows matched in total; a bare array
cannot, and adding it later would break every client. With `fields=`, keys appear in the order asked
for.

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
decimals it just recovered.

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
JobDataControllerTest     9  the brief's URLs end to end, plus null-last ordering and error bodies
RawJobRowTest            12  years parsing: decimals preserved, negative sentinels rejected
BracketFilterSyntaxTest   1  unencoded brackets over a real socket
```

## Layout

```
com.ata.jobdata
├── controller/  JobDataController · JobDataResponse
├── config/      WebConfig
├── exception/   ApiException · ApiExceptionHandler
├── query/       JobField · FilterOperator · QueryParser · QueryParams
├── service/     JobDataService
├── model/       JobRecord
└── repository/  JobDataRepository · InMemoryJobDataRepository · RawJobRow · SalaryParser
```

`model/` has no `entity`/`dto` split: `JobRecord` is a plain record, not a JPA `@Entity` — there is no
database — so a folder named `entity` would claim a mapping that does not exist.

`RawJobRow` mirrors the spreadsheet exactly — string columns keyed by header — so the messy source
format stops at the boundary and never reaches the API.
