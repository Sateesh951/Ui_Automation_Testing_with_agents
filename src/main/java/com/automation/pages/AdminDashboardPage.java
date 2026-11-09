package com.automation.pages;

import com.automation.config.FrameworkConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

public class AdminDashboardPage extends BasePage {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] SUMMARY_LABELS = {
            "Total Categories", "Total Products", "Total SKUs", "Total Coupons"
    };

    public AdminDashboardPage(Page page) {
        super(page);
    }

    public boolean hasExpectedTitle() {
        Locator heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName("Mini's n More Dashboard").setExact(true));
        heading.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        return heading.isVisible();
    }

    public String headingText() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName(Pattern.compile(".*Dashboard.*", Pattern.CASE_INSENSITIVE)))
                .first().innerText().trim();
    }

    public void openDashboard() {
        Locator navigation = page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions()
                .setName("Admin pages").setExact(true));
        navigation.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions()
                .setName("Dashboard").setExact(true)).click();
        page.waitForURL("**/admin", new Page.WaitForURLOptions()
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
    }

    public void openDashboardDirectly() {
        String baseUrl = FrameworkConfig.get("base.url", "");
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("Set base.url or BASE_URL before opening the Dashboard.");
        }
        page.navigate(URI.create(baseUrl).resolve("/admin").toString());
    }

    public boolean hasSidebarNavigation() {
        Locator navigation = page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions()
                .setName("Admin pages").setExact(true));
        return navigation.isVisible();
    }

    public boolean hasSummaryCards() {
        return java.util.Arrays.stream(SUMMARY_LABELS).allMatch(this::hasMetricCard);
    }

    public boolean hasMetricCard(String label) {
        return metricCardText(label).isPresent();
    }

    public boolean metricCardContains(String label, String expected) {
        java.util.Optional<Locator> card = metricCard(label);
        if (card.isEmpty()) return false;
        Locator expectedText = card.get().getByText(expected);
        expectedText.waitFor(new Locator.WaitForOptions().setState(
                com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        return expectedText.isVisible();
    }

    public boolean metricCardHasUnavailableState(String label) {
        java.util.Optional<Locator> card = metricCard(label);
        if (card.isEmpty()) return false;
        Locator unavailable = card.get().getByText(
                Pattern.compile("\\b(unavailable|not available|n/?a)\\b|[—–]", Pattern.CASE_INSENSITIVE));
        unavailable.waitFor(new Locator.WaitForOptions().setState(
                com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        return unavailable.isVisible();
    }

    public boolean metricCardContainsEither(String label, String first, String second) {
        return metricCardText(label).map(text ->
                text.toLowerCase().contains(first.toLowerCase())
                        || text.toLowerCase().contains(second.toLowerCase())).orElse(false);
    }

    public void selectSidebarPage(String pageName) {
        page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions()
                        .setName("Admin pages").setExact(true))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions()
                        .setName(pageName).setExact(true)).click();
        page.waitForURL("**/admin/" + pageName.toLowerCase(),
                new Page.WaitForURLOptions().setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
    }

    public boolean isProductsManagementPageDisplayed() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Products$", Pattern.CASE_INSENSITIVE))).isVisible();
    }

    public void searchSidebar(String query) {
        page.getByRole(AriaRole.SEARCHBOX, new Page.GetByRoleOptions()
                        .setName("Search admin pages").setExact(true))
                .fill(query);
    }

    public boolean isSidebarItemVisible(String item) {
        return page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions()
                        .setName("Admin pages").setExact(true))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions()
                        .setName(item).setExact(true)).isVisible();
    }

    public boolean hasNoUnrelatedSidebarItems(String expectedItem) {
        Locator navigation = page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions()
                .setName("Admin pages").setExact(true));
        Locator links = navigation.getByRole(AriaRole.LINK);
        for (int index = 0; index < links.count(); index++) {
            if (!links.nth(index).innerText().trim().equalsIgnoreCase(expectedItem)) return false;
        }
        return true;
    }

    public void signOut() {
        Locator signOut = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Sign out").setExact(true));
        if (signOut.isVisible()) {
            signOut.click();
            page.waitForURL("**/admin/login", new Page.WaitForURLOptions()
                    .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        }
    }

    public void expireSession() {
        page.context().clearCookies();
        page.evaluate("() => { sessionStorage.setItem('__framework_disable_auth_restore', 'true'); "
                + "localStorage.removeItem('mnm_backend_jwt'); }");
    }

    public void refreshDashboard() {
        page.reload();
    }

    public boolean isLoginPageDisplayed() {
        if (!"/admin/login".equals(URI.create(page.url()).getPath())) return false;
        Locator heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName("Staff sign in").setExact(true));
        heading.waitFor(new Locator.WaitForOptions().setState(
                com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        return heading.isVisible();
    }

    public boolean hasNoDashboardMetrics() {
        return java.util.Arrays.stream(SUMMARY_LABELS).noneMatch(this::hasMetricCard);
    }

    public void simulateSummaryServiceUnavailable() {
        page.route("**/*", route -> {
            String resourceType = route.request().resourceType();
            if ("fetch".equals(resourceType) || "xhr".equals(resourceType)) {
                route.fulfill(new Route.FulfillOptions().setStatus(503)
                        .setContentType("application/json")
                        .setBody("{\"error\":\"Dashboard summary service unavailable\"}"));
            } else {
                route.resume();
            }
        });
    }

    public void simulateMissingSkuMetric() {
        page.route("**/*", route -> {
            String resourceType = route.request().resourceType();
            if (!"fetch".equals(resourceType) && !"xhr".equals(resourceType)) {
                route.resume();
                return;
            }
            APIResponse response = route.fetch();
            String body = response.text();
            try {
                JsonNode payload = JSON.readTree(body);
                if (removeSkuValues(payload)) {
                    route.fulfill(new Route.FulfillOptions().setStatus(response.status())
                            .setHeaders(response.headers()).setBody(JSON.writeValueAsString(payload)));
                } else {
                    route.fulfill(new Route.FulfillOptions().setResponse(response));
                }
            } catch (IOException exception) {
                route.fulfill(new Route.FulfillOptions().setResponse(response));
            }
        });
    }

    public void openFriendlyErrorState() {
        openDashboardDirectly();
        page.waitForLoadState();
    }

    public boolean hasFriendlyServiceError() {
        Locator error = page.getByText(Pattern.compile(
                "unable|unavailable|try again|something went wrong|could not load",
                Pattern.CASE_INSENSITIVE)).first();
        error.waitFor(new Locator.WaitForOptions().setState(
                com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        return error.isVisible();
    }

    public boolean hasNoKnownMetricValues() {
        for (String label : SUMMARY_LABELS) {
            var text = metricCardText(label);
            if (text.isPresent() && text.get().matches("(?s).*\\b(?:9|27|17|3)\\b.*")) return false;
        }
        return true;
    }

    public boolean noSidebarItemsDisplayed() {
        return page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions()
                .setName("Admin pages").setExact(true)).getByRole(AriaRole.LINK).count() == 0;
    }

    public boolean isTextVisible(String text) {
        return page.getByText(text, new Page.GetByTextOptions().setExact(true)).isVisible();
    }

    public boolean protectedDashboardInformationIsHidden() {
        Locator heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName("Mini's n More Dashboard").setExact(true));
        return heading.count() == 0 && hasNoDashboardMetrics();
    }

    private java.util.Optional<String> metricCardText(String label) {
        return metricCard(label).map(Locator::innerText);
    }

    private java.util.Optional<Locator> metricCard(String label) {
        String bodyText = page.locator("body").innerText();
        if (!bodyText.toLowerCase().contains(label.toLowerCase())) return java.util.Optional.empty();
        Locator metricLabel = page.getByText(label).first();
        metricLabel.waitFor(new Locator.WaitForOptions().setState(
                com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        return java.util.Optional.of(metricLabel.locator("xpath=.."));
    }

    private static boolean removeSkuValues(JsonNode node) {
        boolean removed = false;
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey().toLowerCase().contains("sku")) {
                    fields.remove();
                    removed = true;
                } else {
                    removed |= removeSkuValues(field.getValue());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) removed |= removeSkuValues(child);
        }
        return removed;
    }

    public CategoriesPage openCategories() {
        Locator navigation = page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions()
                .setName("Admin pages").setExact(true));
        navigation.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions()
                .setName("Categories").setExact(true)).click();
        page.waitForURL("**/admin/categories", new Page.WaitForURLOptions()
                .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        return new CategoriesPage(page);
    }

    public void signOutIfAuthenticated() {
        Locator signOut = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Sign out").setExact(true));
        if (signOut.count() > 0 && signOut.isVisible()) {
            signOut.click();
            page.waitForURL("**/admin/login", new Page.WaitForURLOptions()
                    .setTimeout(FrameworkConfig.getInt("timeout.ms", 10_000)));
        }
    }
}
