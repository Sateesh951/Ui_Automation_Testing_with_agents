Feature: Mini's n More Admin Dashboard
  As an administrator
  I want to view dashboard information and access admin pages
  So that I can manage the storefront

  Background:
    Given the administrator is logged in to Mini's n More admin portal
    And the administrator is on the Dashboard page

  @positive @smoke
  Scenario: Verify dashboard page loads successfully
    Then the page heading should be "Mini's n More Dashboard"
    And the sidebar navigation should be visible
    And the dashboard summary cards should be visible

  @positive
  Scenario: Verify dashboard summary counts
    Then the "Total Categories" card should display "9"
    And the "Total Products" card should display "27"
    And the "Total SKUs" card should display "17"
    And the "Total Coupons" card should display "3"

  @positive
  Scenario: Verify product status breakdown
    Then the "Total Products" card should display "25 active"
    And the "Total Products" card should display "2 inactive"

  @positive
  Scenario: Navigate to the Products page
    When the administrator selects "Products" from the sidebar
    Then the Products management page should be displayed

  @positive
  Scenario: Search for an admin page using sidebar search
    When the administrator enters "Products" in the sidebar search field
    Then the "Products" navigation item should be displayed
    And unrelated navigation items should not be displayed

  @negative
  Scenario: Prevent dashboard access when the administrator is logged out
    Given the administrator is logged out
    When the user navigates directly to the Dashboard URL
    Then the user should be redirected to the login page
    And dashboard data should not be displayed

  @negative
  Scenario: Handle dashboard summary service failure
    Given the dashboard summary service is unavailable
    When the administrator opens the Dashboard page
    Then a user-friendly error message should be displayed
    And fabricated metric values should not be displayed

  @negative
  Scenario: Handle missing dashboard metric data
    Given the dashboard summary response does not contain the "Total SKUs" value
    When the administrator opens the Dashboard page
    Then the "Total SKUs" card should display an unavailable state
    And the card should not display "undefined" or "null"

  @negative
  Scenario: Display an empty state when sidebar search has no matches
    When the administrator enters "NoSuchAdminPage123" in the sidebar search field
    Then no navigation items should be displayed
    And a "No pages found" message should be displayed

  @negative
  Scenario: Redirect the administrator when the session has expired
    Given the administrator's session has expired
    When the administrator refreshes the Dashboard page
    Then the administrator should be redirected to the login page
    And protected dashboard information should not be visible
