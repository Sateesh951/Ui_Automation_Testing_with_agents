package com.automation.tests.cucumber;

import com.automation.config.FrameworkConfig;
import com.automation.driver.PlaywrightManager;
import com.automation.pages.AdminDashboardPage;
import com.automation.pages.LoginPage;
import com.microsoft.playwright.Page;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.Assert;

import java.nio.file.Path;

public class DashboardSteps {
    private static volatile String authenticationToken;
    private Page page;
    private AdminDashboardPage dashboard;

    @Before("@positive or @negative")
    public void startBrowser() {
        page = PlaywrightManager.start(authenticationToken);
        dashboard = new AdminDashboardPage(page);
    }

    @Given("the administrator is logged in to Mini's n More admin portal")
    public void administratorIsLoggedIn() {
        String username = System.getenv("LOGIN_USERNAME");
        String password = System.getenv("LOGIN_PASSWORD");
        Assert.assertNotNull(username, "LOGIN_USERNAME environment variable is required");
        Assert.assertNotNull(password, "LOGIN_PASSWORD environment variable is required");
        Assert.assertFalse(username.isBlank(), "LOGIN_USERNAME must not be blank");
        Assert.assertFalse(password.isBlank(), "LOGIN_PASSWORD must not be blank");

        if (authenticationToken == null) {
            LoginPage loginPage = new LoginPage(page);
            loginPage.open();
            loginPage.signIn(username, password);
            Object token = page.evaluate("() => localStorage.getItem('mnm_backend_jwt')");
            Assert.assertTrue(token instanceof String && !((String) token).isBlank(),
                    "Login should establish the admin authentication token");
            authenticationToken = (String) token;
            Assert.assertTrue(dashboard.hasExpectedTitle(),
                    "Dashboard should be loaded before reusing the authenticated session");
        } else {
            dashboard.openDashboardDirectly();
            Assert.assertTrue(dashboard.hasExpectedTitle(),
                    "Restored authenticated session should open the Dashboard");
        }
    }

    @Given("the administrator is on the Dashboard page")
    public void administratorIsOnDashboard() {
        dashboard.openDashboardDirectly();
        Assert.assertTrue(dashboard.hasExpectedTitle(), "Dashboard heading should be visible");
    }

    @Then("the page heading should be {string}")
    public void pageHeadingShouldBe(String expectedHeading) {
        Assert.assertEquals(dashboard.headingText(), expectedHeading,
                "Dashboard page heading did not match");
    }

    @Then("the sidebar navigation should be visible")
    public void sidebarNavigationShouldBeVisible() {
        Assert.assertTrue(dashboard.hasSidebarNavigation(), "Admin sidebar should be visible");
    }

    @Then("the dashboard summary cards should be visible")
    public void dashboardSummaryCardsShouldBeVisible() {
        Assert.assertTrue(dashboard.hasSummaryCards(),
                "Dashboard summary cards should include categories, products, SKUs, and coupons");
    }

    @Then("the {string} card should display {string}")
    public void metricCardShouldDisplay(String cardName, String expectedValue) {
        Assert.assertTrue(dashboard.metricCardContains(cardName, expectedValue),
                "Expected '" + expectedValue + "' in the '" + cardName + "' card");
    }

    @When("the administrator selects {string} from the sidebar")
    public void administratorSelectsSidebarPage(String pageName) {
        dashboard.selectSidebarPage(pageName);
    }

    @Then("the Products management page should be displayed")
    public void productsManagementPageShouldBeDisplayed() {
        Assert.assertTrue(dashboard.isProductsManagementPageDisplayed(),
                "Products management page heading should be visible");
    }

    @When("the administrator enters {string} in the sidebar search field")
    public void administratorEntersSidebarSearch(String query) {
        dashboard.searchSidebar(query);
    }

    @Then("the {string} navigation item should be displayed")
    public void navigationItemShouldBeDisplayed(String item) {
        Assert.assertTrue(dashboard.isSidebarItemVisible(item),
                "Expected sidebar navigation item to be visible: " + item);
    }

    @Then("unrelated navigation items should not be displayed")
    public void unrelatedNavigationItemsShouldNotBeDisplayed() {
        Assert.assertTrue(dashboard.hasNoUnrelatedSidebarItems("Products"),
                "Sidebar search should hide navigation items unrelated to Products");
    }

    @Given("the administrator is logged out")
    public void administratorIsLoggedOut() {
        dashboard.expireSession();
    }

    @When("the user navigates directly to the Dashboard URL")
    public void userNavigatesDirectlyToDashboard() {
        dashboard.openDashboardDirectly();
    }

    @Then("the user should be redirected to the login page")
    @Then("the administrator should be redirected to the login page")
    public void userShouldBeRedirectedToLogin() {
        Assert.assertTrue(dashboard.isLoginPageDisplayed(),
                "Unauthenticated user should be redirected to the staff login page");
    }

    @Then("dashboard data should not be displayed")
    public void dashboardDataShouldNotBeDisplayed() {
        Assert.assertTrue(dashboard.hasNoDashboardMetrics(),
                "Dashboard summary data must not be visible while logged out");
    }

    @Given("the dashboard summary service is unavailable")
    public void dashboardSummaryServiceIsUnavailable() {
        dashboard.simulateSummaryServiceUnavailable();
    }

    @When("the administrator opens the Dashboard page")
    public void administratorOpensDashboard() {
        dashboard.openFriendlyErrorState();
    }

    @Then("a user-friendly error message should be displayed")
    public void userFriendlyErrorMessageShouldBeDisplayed() {
        Assert.assertTrue(dashboard.hasFriendlyServiceError(),
                "A user-friendly dashboard service error should be visible");
    }

    @Then("fabricated metric values should not be displayed")
    public void fabricatedMetricValuesShouldNotBeDisplayed() {
        Assert.assertTrue(dashboard.hasNoKnownMetricValues(),
                "Known dashboard metric counts must not be shown after the service fails");
    }

    @Given("the dashboard summary response does not contain the {string} value")
    public void summaryResponseOmitsMetric(String metricName) {
        Assert.assertEquals(metricName, "Total SKUs",
                "The missing-metric simulation is configured for the Total SKUs metric");
        dashboard.simulateMissingSkuMetric();
    }

    @Then("the {string} card should display an unavailable state")
    public void metricCardShouldDisplayUnavailableState(String cardName) {
        Assert.assertTrue(dashboard.metricCardHasUnavailableState(cardName),
                "Expected an unavailable state for the '" + cardName + "' card");
    }

    @Then("the card should not display {string} or {string}")
    public void metricCardShouldNotDisplayValues(String first, String second) {
        Assert.assertFalse(dashboard.metricCardContainsEither("Total SKUs", first, second),
                "The metric card must not expose undefined or null values");
    }

    @Then("no navigation items should be displayed")
    public void noNavigationItemsShouldBeDisplayed() {
        Assert.assertTrue(dashboard.noSidebarItemsDisplayed(),
                "No sidebar navigation items should match the search");
    }

    @Then("a {string} message should be displayed")
    public void messageShouldBeDisplayed(String message) {
        Assert.assertTrue(dashboard.isTextVisible(message),
                "Expected message to be visible: " + message);
    }

    @Given("the administrator's session has expired")
    public void administratorSessionHasExpired() {
        dashboard.expireSession();
    }

    @When("the administrator refreshes the Dashboard page")
    public void administratorRefreshesDashboard() {
        dashboard.refreshDashboard();
    }

    @Then("protected dashboard information should not be visible")
    public void protectedDashboardInformationShouldNotBeVisible() {
        Assert.assertTrue(dashboard.protectedDashboardInformationIsHidden(),
                "Protected dashboard information must not be visible after session expiry");
    }

    @After("@positive or @negative")
    public void captureFailureAndClose(Scenario scenario) {
        try {
            if (scenario.isFailed() && page != null && !page.isClosed()) {
                String safeName = scenario.getName().replaceAll("[^A-Za-z0-9._-]", "_");
                Path screenshotPath = Path.of(System.getProperty("framework.report.dir", "reports"))
                        .resolve("UI").resolve("screenshots")
                        .resolve(safeName + "-" + System.nanoTime() + ".png");
                String screenshot = PlaywrightManager.screenshot(screenshotPath);
                if (screenshot != null) {
                    scenario.attach(java.nio.file.Files.readAllBytes(Path.of(screenshot)),
                            "image/png", "Dashboard scenario failure");
                    StepReportPlugin.recordScreenshot(scenario.getName(), Path.of(screenshot));
                }
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to attach Dashboard failure screenshot.", exception);
        } finally {
            PlaywrightManager.close();
        }
    }

    @AfterAll
    public static void clearAuthenticatedStorageState() {
        authenticationToken = null;
    }
}
