# Copyright (c) 2017-2022 SUSE LLC
# Licensed under the terms of the MIT license.

@scope_salt_ssh
@scope_onboarding
@ssh_minion
Feature: Register a salt-ssh system via API

  Scenario: Log in as admin user
    Given I am authorized for the "Admin" section
    And I am logged in API as user "admin" and password "admin"

  Scenario: Delete SSH minion system profile before API bootstrap test
    Given I am on the Systems overview page of this "ssh_minion"
    When I follow "Delete System"
    Then I should see a "Confirm System Profile Deletion" text
    When I click on "Delete Profile"
    And I wait until I see "has been deleted" text
    Then "ssh_minion" should not be registered

@proxy
  Scenario: block direct access from server to sshminion to test proxy as jumphost
    Given I block connections from "server" on "ssh_minion"

  Scenario: Bootstrap a SLES SSH minion via API
    When I call system.bootstrap() on host "ssh_minion" and salt-ssh "enabled"

  Scenario: Check new API bootstrapped salt-ssh system in System Overview page
    When I follow the left menu "Systems > System List > All"
    And I wait until I see the name of "ssh_minion", refreshing the page
    And I wait until onboarding is completed for "ssh_minion"

  Scenario: Check contact method of this Salt SSH system
    Given I am on the Systems overview page of this "ssh_minion"
    Then I should see a "Push via SSH" text

@proxy
  Scenario: Check registration on proxy of SSH minion bootstrapped via API
    Given I am on the Systems overview page of this "proxy"
    When I follow "Details" in the content area
    And I follow "Proxy" in the content area
    Then I should see "ssh_minion" hostname

  Scenario: Check spacecmd system ID of SSH minion bootstrapped via API
    Given I am on the Systems overview page of this "ssh_minion"
    Then I run spacecmd listevents for "ssh_minion"

  Scenario: Check events history for failures on SSH minion after API bootstrap
    Given I am on the Systems overview page of this "ssh_minion"
    Then I check for failed events on history event page

  Scenario: API bootstrap: subscribe SSH minion to base channel
    Given I am on the Systems overview page of this "ssh_minion"
    When I follow "Software" in the content area
    And I follow "Software Channels" in the content area
    And I wait until I do not see "Loading..." text
    And I check radio button "SLE-Product-SLES15-SP4-Pool for x86_64"
    And I wait until I do not see "Loading..." text
    And I include the recommended child channels
    And I check "SLE-Module-DevTools15-SP4-Pool for x86_64"
    And I check "Fake-RPM-SLES-Channel"
    And I click on "Next"
    Then I should see a "Confirm Software Channel Change" text
    When I click on "Confirm"
    Then I should see a "Changing the channels has been scheduled." text
    And I wait until event "Subscribe channels scheduled by admin" is completed

@proxy
  Scenario: cleanup and flush the firewall rules
    When I flush firewall on "ssh_minion"

  Scenario: Cleanup: Logout from API
    When I logout from API
