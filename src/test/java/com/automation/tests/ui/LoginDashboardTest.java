package com.automation.tests.ui;

import com.automation.config.FrameworkConfig;
import com.automation.driver.PlaywrightManager;
import com.automation.pages.AdminDashboardPage;
import com.automation.pages.CategoriesPage;
import com.automation.pages.LoginPage;
import com.automation.testdata.CaseMetadata;
import com.microsoft.playwright.Page;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;

public class LoginDashboardTest {
    @Test(groups = {"ui", "smoke", "Login"})
    @CaseMetadata(
            id = "LOGIN-NAV-001",
            module = "Login",
            description = "Sign in, verify dashboard and Categories, then sign out",
            expectedResult = "Dashboard title and Categories page are visible; user is signed out")
    public void signInVerifyDashboardCategoriesAndSignOut() {
        String username = System.getenv("LOGIN_USERNAME");
        String password = System.getenv("LOGIN_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new SkipException("Set LOGIN_USERNAME and LOGIN_PASSWORD environment variables.");
        }

        Page page = PlaywrightManager.start();
        CopyOnWriteArrayList<String> consoleErrors = new CopyOnWriteArrayList<>();
        page.onConsoleMessage(message -> {
            if ("error".equalsIgnoreCase(message.type())) consoleErrors.add(message.text());
        });

        Throwable testFailure = null;
        try {
            LoginPage loginPage = new LoginPage(page);
            loginPage.open();
            loginPage.signIn(username, password);

            AdminDashboardPage dashboard = new AdminDashboardPage(page);
            dashboard.openDashboard();
            Assert.assertTrue(dashboard.hasExpectedTitle(),
                    "Expected dashboard heading: Mini's n More Dashboard");

            CategoriesPage categories = dashboard.openCategories();
            Assert.assertTrue(categories.hasExpectedHeading(),
                    "Expected Categories page heading to be visible");

            dashboard.signOutIfAuthenticated();
            Assert.assertTrue(page.url().endsWith("/admin/login"),
                    "Expected sign out to return to the staff login page");
            Assert.assertTrue(new LoginPage(page).locator("role", "heading", "Staff sign in").isVisible(),
                    "Expected the Staff sign in heading after logout");
        } catch (RuntimeException | AssertionError failure) {
            testFailure = failure;
            String screenshot = PlaywrightManager.screenshot(
                    Path.of(System.getProperty("framework.report.dir", "reports"))
                            .resolve("UI").resolve("screenshots").resolve("LOGIN-NAV-001.png"));
            if (screenshot != null) {
                org.testng.Reporter.getCurrentTestResult().setAttribute("screenshot", screenshot);
            }
            if (!consoleErrors.isEmpty()) {
                org.testng.Reporter.getCurrentTestResult().setAttribute("console.errors", consoleErrors);
            }
            throw failure;
        } finally {
            try {
                new AdminDashboardPage(page).signOutIfAuthenticated();
            } catch (RuntimeException logoutFailure) {
                if (testFailure != null) {
                    testFailure.addSuppressed(logoutFailure);
                } else {
                    throw logoutFailure;
                }
            } finally {
                PlaywrightManager.close();
            }
        }
    }
}
