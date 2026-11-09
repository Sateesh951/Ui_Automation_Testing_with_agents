package com.automation.tests.ui;

import com.automation.config.FrameworkConfig;
import com.automation.driver.PlaywrightManager;
import com.automation.pages.BasePage;
import com.automation.testdata.TestCaseDefinition;
import com.automation.testdata.TestCaseStore;
import com.automation.testdata.UiStep;
import com.automation.utilities.ValueResolver;
import com.microsoft.playwright.Page;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.net.URI;
import java.util.concurrent.CopyOnWriteArrayList;

public class DataDrivenUiTest {
    @DataProvider(name = "uiCases")
    public static Object[][] uiCases() {
        return TestCaseStore.casesFor("UI");
    }

    @Test(dataProvider = "uiCases", groups = {"ui"})
    public void executeUiCase(TestCaseDefinition testCase) {
        if (testCase.ui == null || testCase.ui.url == null || testCase.ui.steps == null
                || testCase.ui.steps.isEmpty()) {
            throw new SkipException("UI URL and at least one explicit UI step are required.");
        }
        String baseUrl = FrameworkConfig.get("base.url", "");
        if (baseUrl.isBlank()) throw new SkipException("Set base.url (or BASE_URL) to run UI cases.");

        Page page = PlaywrightManager.start();
        CopyOnWriteArrayList<String> consoleErrors = new CopyOnWriteArrayList<>();
        page.onConsoleMessage(message -> {
            if ("error".equalsIgnoreCase(message.type())) {
                consoleErrors.add(message.text());
            }
        });
        try {
            String targetUrl = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                    .resolve(ValueResolver.resolve(testCase.ui.url)).toString();
            page.navigate(targetUrl);
            BasePage basePage = new BasePage(page);
            SoftAssert assertions = new SoftAssert();
            for (UiStep step : testCase.ui.steps) {
                String action = step.action == null ? "" : step.action;
                switch (action) {
                    case "navigate" -> page.navigate(URI.create(targetUrl)
                            .resolve(ValueResolver.resolve(step.value)).toString());
                    case "handleNextDialog" -> page.onceDialog(dialog -> {
                        if ("dismiss".equalsIgnoreCase(step.value)) dialog.dismiss();
                        else if ("accept".equalsIgnoreCase(step.value)) dialog.accept();
                        else throw new IllegalArgumentException("Dialog action must be accept or dismiss.");
                    });
                    case "click" -> basePage.locator(step.by, step.selector, step.name).click();
                    case "fill" -> basePage.locator(step.by, step.selector, step.name)
                            .fill(ValueResolver.resolve(step.value));
                    case "selectOption" -> basePage.locator(step.by, step.selector, step.name)
                            .selectOption(ValueResolver.resolve(step.value));
                    case "frameClick" -> page.frameLocator(step.frameSelector).locator(step.selector).click();
                    case "frameFill" -> page.frameLocator(step.frameSelector).locator(step.selector)
                            .fill(ValueResolver.resolve(step.value));
                    case "clickAndSwitchToPopup" -> {
                        com.microsoft.playwright.Locator popupTrigger =
                                basePage.locator(step.by, step.selector, step.name);
                        page = page.waitForPopup(popupTrigger::click);
                        PlaywrightManager.setPage(page);
                        basePage = new BasePage(page);
                    }
                    case "assertVisible" -> {
                        com.microsoft.playwright.Locator locator =
                                basePage.locator(step.by, step.selector, step.name);
                        locator.waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
                        assertions.assertTrue(locator.isVisible(), "Expected visible element: " + step.selector);
                    }
                    case "assertText" -> assertions.assertEquals(
                            basePage.locator(step.by, step.selector, step.name).innerText(),
                            ValueResolver.resolve(step.value), "Text mismatch for: " + step.selector);
                    case "assertUrl" -> {
                        String expectedUrl = URI.create(targetUrl)
                                .resolve(ValueResolver.resolve(step.value)).toString();
                        page.waitForURL(expectedUrl, new Page.WaitForURLOptions()
                                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
                        assertions.assertEquals(page.url(), expectedUrl, "Page URL mismatch");
                    }
                    default -> throw new IllegalArgumentException("Unsupported UI action: " + action);
                }
            }
            assertions.assertAll();
        } catch (RuntimeException | AssertionError failure) {
            String screenshot = PlaywrightManager.screenshot(
                    java.nio.file.Path.of(System.getProperty("framework.report.dir", "reports"))
                            .resolve("UI").resolve("screenshots").resolve(safeName(testCase.id) + ".png"));
            if (screenshot != null) {
                org.testng.Reporter.getCurrentTestResult().setAttribute("screenshot", screenshot);
            }
            if (!consoleErrors.isEmpty())
                org.testng.Reporter.getCurrentTestResult().setAttribute("console.errors", consoleErrors);
            throw failure;
        } finally {
            PlaywrightManager.close();
        }
    }

    private static String safeName(String value) {
        return value == null ? "unnamed-case" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
