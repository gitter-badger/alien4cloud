Feature: Check a property constraint

Background:
  Given I am authenticated with "ADMIN" role
  And I load several configuration tags
  Then I should have 6 configuration tags loaded 

Scenario: I can check properties constraint validValues in A,B,C,D
  Given I am authenticated with "ADMIN" role
  And I have the tag "_ALIEN_LIST" registered for applications
  When I create a new application with name "watchmiddleearth" and description "Use my great eye to find frodo and the ring."
  And I fill the value "TT" for "_ALIEN_LIST" tag to check
  Then I should receive a RestResponse with an error code 800
  When I fill the value "A" for "_ALIEN_LIST" tag to check
  Then I should receive a RestResponse with no error
  

Scenario: I can check properties constraint min length equals 4
  Given I am authenticated with "ADMIN" role
  And I have the tag "_ALIEN_PASSWORD_MIN4" registered for applications
  When I create a new application with name "watchmiddleearth" and description "Use my great eye to find frodo and the ring."
  And I fill the value "AAA" for "_ALIEN_PASSWORD_MIN4" tag to check
  Then I should receive a RestResponse with an error code 800
  When I fill the value "ZZZZ" for "_ALIEN_PASSWORD_MIN4" tag to check
  Then I should receive a RestResponse with no error   