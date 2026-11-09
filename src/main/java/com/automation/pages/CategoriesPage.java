package com.automation.pages;

import com.automation.config.FrameworkConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class CategoriesPage extends BasePage {
    public CategoriesPage(Page page) {
        super(page);
    }

    public boolean hasExpectedHeading() {
        Locator heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName("Categories").setExact(true));
        heading.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        return heading.isVisible();
    }
}
