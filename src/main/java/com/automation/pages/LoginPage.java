package com.automation.pages;

import com.automation.config.FrameworkConfig;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.net.URI;

public class LoginPage extends BasePage {
    public LoginPage(Page page) {
        super(page);
    }

    public void open() {
        String baseUrl = FrameworkConfig.get("base.url", "");
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("Set base.url (or BASE_URL) before running login tests.");
        }
        page.navigate(URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                .resolve("/admin/login").toString());
    }

    public void signIn(String username, String password) {
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions()
                        .setName("you@store.in").setExact(true))
                .fill(username);
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions()
                        .setName("Password (min 6)").setExact(true))
                .fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName("Sign in").setExact(true))
                .click();
        page.waitForURL("**/admin", new Page.WaitForURLOptions()
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
    }
}
