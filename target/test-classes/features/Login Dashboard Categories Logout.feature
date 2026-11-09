Feature: Staff login and admin navigation
  As an authorized staff user
  I want to sign in and navigate the admin area
  So that I can verify Dashboard and Categories access and sign out safely

  Background:
    Given the Mini's n More UAT application is available

  @ui @smoke @login
  Scenario: Sign in, verify Dashboard and Categories, then sign out
    Given I am on the staff sign-in page
    When I sign in with valid staff credentials
    And I click "Dashboard" in the admin navigation
    Then I should see the "Mini's n More Dashboard" heading
    When I click "Categories" in the admin navigation
    Then I should see the "Categories" heading
    When I click "Sign out"
    Then I should be returned to the staff sign-in page