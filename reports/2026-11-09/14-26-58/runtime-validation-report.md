# Runtime Validation Report

**Generated**: 2026-11-09T14:27:22+05:30  
**Target**: `https://uat.minisnmore.com`

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | External UAT application was reachable during the browser scenario. No local application was started. |
| Integration Tests | UNVERIFIED | n/a | No separate integration suite was run. |
| E2E Tests | PASS | 0 | 1 Cucumber UI scenario passed; all 9 Gherkin steps passed in headless mode. |

**Overall**: PASS

## Test Evidence

- Command: `mvn test -DsuiteXmlFile=src/test/resources/testng-cucumber.xml -Dframework.headless=true`
- Surefire results: `target/surefire-reports/`
- Execution dashboard: `index.html`
- Step-by-step report: `cucumber-step-report.html`
- Summary: `execution-summary.json`

## Environment

- Docker: unavailable; daemon not responding. Not needed for this external UAT browser test.
- Node.js: available (`v24.22.1`).
- Browser: Java Playwright launched Chromium successfully.
