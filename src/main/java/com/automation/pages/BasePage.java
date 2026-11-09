package com.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class BasePage {
    protected final Page page;

    public BasePage(Page page) {
        this.page = page;
    }

    public Locator locator(String selector) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("A non-empty locator selector is required.");
        }
        return page.locator(selector);
    }

    public Locator locator(String strategy, String selector) {
        return locator(strategy, selector, null);
    }

    public Locator locator(String strategy, String selector, String name) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("A non-empty locator value is required.");
        }
        return switch (strategy == null ? "css" : strategy) {
            case "css" -> page.locator(selector);
            case "role" -> name == null || name.isBlank()
                    ? page.getByRole(AriaRole.valueOf(selector.toUpperCase()))
                    : page.getByRole(AriaRole.valueOf(selector.toUpperCase()),
                            new Page.GetByRoleOptions().setName(name).setExact(true));
            case "label" -> page.getByLabel(selector);
            case "text" -> page.getByText(selector);
            case "testId" -> page.getByTestId(selector);
            default -> throw new IllegalArgumentException("Unsupported locator strategy: " + strategy);
        };
    }
}
