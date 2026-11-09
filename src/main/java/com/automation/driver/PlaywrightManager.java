package com.automation.driver;

import com.automation.config.FrameworkConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class PlaywrightManager {
    private static final Logger LOGGER = LogManager.getLogger(PlaywrightManager.class);
    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    private PlaywrightManager() {}

    public static Page start() {
        return start(null);
    }

    public static Page start(String authenticationToken) {
        close();
        Playwright playwright = Playwright.create();
        String browserName = FrameworkConfig.get("browser", "chromium").toLowerCase();
        BrowserType browserType = switch (browserName) {
            case "chromium" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalArgumentException("Unsupported browser: " + browserName);
        };
        Browser browser = browserType.launch(new BrowserType.LaunchOptions()
                .setHeadless(FrameworkConfig.getBoolean("headless", true)));
        BrowserContext context = browser.newContext();
        if (authenticationToken != null) {
            String origin = java.net.URI.create(FrameworkConfig.get("base.url", "")).resolve("/").getScheme()
                    + "://" + java.net.URI.create(FrameworkConfig.get("base.url", "")).getAuthority();
            String tokenBase64 = Base64.getEncoder().encodeToString(
                    authenticationToken.getBytes(StandardCharsets.UTF_8));
            context.addInitScript("if (location.origin === '" + origin
                    + "' && sessionStorage.getItem('__framework_disable_auth_restore') !== 'true') "
                    + "localStorage.setItem('mnm_backend_jwt', atob('" + tokenBase64 + "'));");
        }
        context.setDefaultTimeout(FrameworkConfig.getInt("timeout.ms", 10_000));
        Page page = context.newPage();
        PLAYWRIGHT.set(playwright);
        BROWSER.set(browser);
        CONTEXT.set(context);
        PAGE.set(page);
        return page;
    }

    public static Page page() {
        Page page = PAGE.get();
        if (page == null) throw new IllegalStateException("No Playwright page exists on this thread.");
        return page;
    }

    public static void setPage(Page page) {
        PAGE.set(page);
    }

    public static String screenshot(Path destination) {
        Page page = PAGE.get();
        if (page == null || page.isClosed()) return null;
        try {
            Files.createDirectories(destination.getParent());
            page.screenshot(new Page.ScreenshotOptions().setPath(destination).setFullPage(true));
            return destination.toString();
        } catch (java.io.IOException | RuntimeException exception) {
            LOGGER.error("Unable to capture failure screenshot at {}", destination, exception);
            return null;
        }
    }

    public static void close() {
        try {
            if (CONTEXT.get() != null) CONTEXT.get().close();
        } finally {
            try {
                if (BROWSER.get() != null) BROWSER.get().close();
            } finally {
                if (PLAYWRIGHT.get() != null) PLAYWRIGHT.get().close();
                PAGE.remove();
                CONTEXT.remove();
                BROWSER.remove();
                PLAYWRIGHT.remove();
            }
        }
    }
}
