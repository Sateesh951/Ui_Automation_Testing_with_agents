package com.automation.tests.cucumber;

import com.automation.config.FrameworkConfig;
import com.automation.driver.PlaywrightManager;
import com.automation.pages.AdminDashboardPage;
import com.automation.pages.CategoriesPage;
import com.automation.pages.LoginPage;
import com.microsoft.playwright.Page;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.Assert;

import java.nio.file.Path;

public class LoginNavigationSteps {
    private Page page;
    private LoginPage loginPage;
    private AdminDashboardPage dashboard;
    private CategoriesPage categories;

    @Before("@ui")
    public void startBrowser() {
        page = PlaywrightManager.start();
        loginPage = new LoginPage(page);
    }

    @Given("the Mini's n More UAT application is available")
    public void applicationIsAvailable() {
        String baseUrl = FrameworkConfig.get("base.url", "");
        Assert.assertFalse(baseUrl.isBlank(), "Set base.url or BASE_URL before running the feature.");
    }

    @Given("I am on the staff sign-in page")
    public void openStaffSignInPage() {
        loginPage.open();
        Assert.assertTrue(loginPage.locator("role", "heading", "Staff sign in").isVisible(),
                "Staff sign-in heading should be visible");
    }

    @When("I sign in with valid staff credentials")
    public void signInWithValidCredentials() {
        String username = System.getenv("LOGIN_USERNAME");
        String password = System.getenv("LOGIN_PASSWORD");
        Assert.assertNotNull(username, "LOGIN_USERNAME environment variable is required");
        Assert.assertNotNull(password, "LOGIN_PASSWORD environment variable is required");
        Assert.assertFalse(username.isBlank(), "LOGIN_USERNAME must not be blank");
        Assert.assertFalse(password.isBlank(), "LOGIN_PASSWORD must not be blank");
        loginPage.signIn(username, password);
        dashboard = new AdminDashboardPage(page);
    }

    @When("I click {string} in the admin navigation")
    public void clickAdminNavigation(String item) {
        switch (item) {
            case "Dashboard" -> dashboard.openDashboard();
            case "Categories" -> categories = dashboard.openCategories();
            default -> throw new IllegalArgumentException("Unsupported admin navigation item: " + item);
        }
    }

    @Then("I should see the {string} heading")
    public void verifyHeading(String expectedHeading) {
        boolean visible = switch (expectedHeading) {
            case "Mini's n More Dashboard" -> dashboard.hasExpectedTitle();
            case "Categories" -> categories != null && categories.hasExpectedHeading();
            default -> page.getByText(expectedHeading, new Page.GetByTextOptions().setExact(true)).isVisible();
        };
        Assert.assertTrue(visible, "Expected visible heading: " + expectedHeading);
    }

    @When("I click {string}")
    public void clickButton(String buttonName) {
        if (!"Sign out".equals(buttonName)) {
            throw new IllegalArgumentException("Unsupported button: " + buttonName);
        }
        dashboard.signOutIfAuthenticated();
    }

    @Then("I should be returned to the staff sign-in page")
    public void verifyReturnedToSignInPage() {
        Assert.assertTrue(page.url().endsWith("/admin/login"),
                "Expected to be returned to staff sign in, actual URL: " + page.url());
        Assert.assertTrue(loginPage.locator("role", "heading", "Staff sign in").isVisible(),
                "Staff sign-in heading should be visible after sign out");
    }

    @After("@ui")
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
                            "image/png", "Login scenario failure");
                    StepReportPlugin.recordScreenshot(scenario.getName(), Path.of(screenshot));
                }
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to attach Cucumber failure screenshot.", exception);
        } finally {
            if (page != null && !page.isClosed()) {
                new AdminDashboardPage(page).signOutIfAuthenticated();
            }
            PlaywrightManager.close();
        }
    }
}
