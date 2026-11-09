# UI and API Automation Framework

Java 21, Maven, TestNG, Playwright Java, REST Assured, Jackson, Extent Reports and Log4j2.

## BDD workflow

UI scenarios are written as Cucumber `.feature` files under `src/test/resources/features/` and run by Cucumber's TestNG runner. Step definitions live in `src/test/java/com/automation/tests/cucumber/`; shared Playwright lifecycle and page objects are reused across modules. When you provide a feature file, the matching step definitions are implemented or reused, and the scenarios are run with step-by-step reports. The framework does not guess missing URLs, selectors, API contracts, or expected outcomes.

API tests remain data-driven TestNG tests using `testdata/test-cases.json` and REST Assured. They run alongside the Cucumber scenarios in the default suite.

## Run

Install the Playwright browser binaries once:

```powershell
mvn -q exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

```powershell
mvn test                                      # Cucumber UI features and API tests
mvn test -DsuiteXmlFile=src/test/resources/testng-ui.xml -Dframework.headless=false
mvn test -DsuiteXmlFile=src/test/resources/testng-cucumber.xml -Dframework.headless=false
mvn test -DsuiteXmlFile=src/test/resources/testng-api.xml
mvn test -Dcucumber.filter.tags="@smoke"
mvn test -Dcucumber.filter.tags="@login and not @wip"
mvn test -Dtest=DataDrivenApiTest             # one API test class
```

Set `base.url` / `api.base.url` and other values in `src/test/resources/config.properties`, environment variables (`BASE_URL`, `API_BASE_URL`, `BROWSER`, `HEADLESS`), or JVM properties (`-Dframework.base.url=...`). Login UI credentials are read from `LOGIN_USERNAME` and `LOGIN_PASSWORD` environment variables; never put secrets in source code or case data.

The Login Gherkin scenario in `src/test/resources/features/Login Dashboard Categories Logout.feature` runs through `CucumberTestRunner`. Set `LOGIN_USERNAME`, `LOGIN_PASSWORD`, and `BASE_URL`, then use `mvn test -DsuiteXmlFile=src/test/resources/testng-cucumber.xml -Dframework.headless=false` for headed execution. Cucumber runs create `cucumber-step-report.html` and `cucumber-step-report.json` in the timestamped report directory, listing each Given/When/Then step with status, duration, and failure details. The suite dashboard includes scenario records as well as detailed step reports. Screenshots are saved and attached when a scenario fails.

### Adding a module

1. Add the module's `.feature` files under `src/test/resources/features/` (for example `Orders.feature`).
2. Tag browser-driven scenarios with `@ui`; add selection tags such as `@smoke` or `@regression`.
3. Use existing common step definitions where they fit. Add module-specific definitions under `src/test/java/com/automation/tests/cucumber/` and reusable page objects under `src/main/java/com/automation/pages/`.
4. Provide only the environment values the feature needs. UI login uses `LOGIN_USERNAME` and `LOGIN_PASSWORD`; application URL uses `BASE_URL`.
5. Run the module/tag and inspect the timestamped dashboard and `cucumber-step-report.html`. A failing UI scenario saves a screenshot under `UI/screenshots/`.

The `@ui` tag scopes browser creation/cleanup hooks. API-oriented Cucumber scenarios can use their own step definitions without opening a browser; API TestNG cases can also stay in the existing data-driven suite.

Supported browsers are `chromium`, `firefox`, and `webkit`; configure headed mode with `-Dframework.headless=false`. Install the corresponding Playwright browser binary before running UI cases.

## API case definition

API case example for the optional data-driven TestNG API runner:

```json
{
  "id": "HEALTH-001",
  "module": "Health",
  "description": "Health endpoint returns ready",
  "type": "API",
  "expectedResult": "HTTP 200 and status is ready",
  "tags": ["smoke"],
  "api": {
    "method": "GET",
    "path": "/health",
    "expectedStatus": 200,
    "assertions": [{"jsonPath": "status", "equals": "ready"}]
  }
}
```

API supports GET, POST, PUT, PATCH, DELETE, headers, query/path params, request bodies, expected response headers, JSONPath assertions, optional JSON schema validation (`responseSchema` is a classpath resource), and response-time thresholds. Set `contentType` and `accept` for XML or other media types; XML payloads can be supplied as strings. POJOs and maps are serialized by REST Assured/Jackson. For credential values, use `${ENV_VAR}` in headers, values, and body strings. `testdata/test-cases.json` starts empty intentionally.

## Reports

Each TestNG run creates `UI_Automation_Testing/dd-MMM-yyyy/HH-mm-ss/` using the `Asia/Kolkata` time zone (for example `UI_Automation_Testing/09-Nov-2026/14-43-00/`). Each run has its own directory with Extent and summary HTML/JSON, UI/API results pages, failure screenshots, API failure logs and an execution log. The directory is initialized by the suite listener; parallel tests use thread-local browser and report state.
