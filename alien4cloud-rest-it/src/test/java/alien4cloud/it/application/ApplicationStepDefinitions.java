package alien4cloud.it.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.collect.Maps;
import org.junit.Assert;

import alien4cloud.component.model.Tag;
import alien4cloud.dao.model.FacetedSearchResult;
import alien4cloud.it.Context;
import alien4cloud.it.common.CommonStepDefinitions;
import alien4cloud.it.security.AuthenticationStepDefinitions;
import alien4cloud.model.application.Application;
import alien4cloud.rest.application.CreateApplicationRequest;
import alien4cloud.rest.component.SearchRequest;
import alien4cloud.rest.component.UpdateTagRequest;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.topology.TopologyDTO;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.tosca.container.model.topology.NodeTemplate;
import alien4cloud.tosca.container.model.topology.TopologyTemplate;
import alien4cloud.utils.ReflectionUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import cucumber.api.DataTable;
import cucumber.api.PendingException;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class ApplicationStepDefinitions {
    public static Application CURRENT_APPLICATION;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    public static Map<String, Application> CURRENT_APPLICATIONS = Maps.newHashMap(); // | APP_NAME -> APP_OBJECT |

    private AuthenticationStepDefinitions authSteps = new AuthenticationStepDefinitions();
    private CommonStepDefinitions commonStepDefinitions = new CommonStepDefinitions();

    @When("^I create a new application with name \"([^\"]*)\" and description \"([^\"]*)\"$")
    public void I_create_a_new_application_with_name_and_description(String name, String description) throws Throwable {
        CreateApplicationRequest request = new CreateApplicationRequest(name, description, null);
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().postJSon("/rest/applications/", JsonUtil.toString(request)));
        RestResponse<String> reponse = JsonUtil.read(Context.getInstance().getRestResponse(), String.class);
        String applicationJson = Context.getRestClientInstance().get("/rest/applications/" + reponse.getData());
        RestResponse<Application> application = JsonUtil.read(applicationJson, Application.class);
        CURRENT_APPLICATION = (application.getData() == null) ? new Application() : application.getData();

        Context.getInstance().registerApplication(CURRENT_APPLICATION);
        if (application.getData() != null) {
            String topologyId = getTopologyIdFromApplication(application.getData().getId());
            Context.getInstance().registerTopologyId(topologyId);
        }
    }

    private String getTopologyIdFromApplication(String applicationId) throws IOException {
        String response = Context.getRestClientInstance().get("/rest/applications/" + applicationId + "/topology");
        return JsonUtil.read(response, String.class).getData();
    }

    @Then("^The RestResponse should contain an id string$")
    public void The_RestResponse_should_contain_an_id_string() throws Throwable {
        String response = Context.getInstance().getRestResponse();
        assertNotNull(response);
        RestResponse<String> restResponse = JsonUtil.read(response, String.class);
        assertNotNull(restResponse);
        assertNotNull(restResponse.getData());
        assertEquals(String.class, restResponse.getData().getClass());
    }

    @When("^I create a new application with name \"([^\"]*)\" and description \"([^\"]*)\" based on this created template$")
    public void I_create_a_new_application_with_name_and_description_based_this_created_template(String name, String description) throws Throwable {

        // recover the created template
        TopologyTemplate template = Context.getInstance().getTopologyTemplate();
        assertNotNull(template);

        // create the application linked to this template
        CreateApplicationRequest request = new CreateApplicationRequest(name, description, template.getTopologyId());
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().postJSon("/rest/applications/", JsonUtil.toString(request)));

        // check the created application (topologyId)
        RestResponse<String> reponse = JsonUtil.read(Context.getInstance().getRestResponse(), String.class);
        String applicationJson = Context.getRestClientInstance().get("/rest/applications/" + reponse.getData());
        Application application = JsonUtil.read(applicationJson, Application.class).getData();

        assertNotNull(application);
        CURRENT_APPLICATION = application;

        assertNotNull(getTopologyIdFromApplication(application.getId()));

    }

    @Then("^The created application topology is the same as the one in the base topology template$")
    public void The_created_application_topology_is_the_same_as_the_one_in_the_base_topology_template() throws Throwable {
        // created topology
        TopologyTemplate template = Context.getInstance().getTopologyTemplate();

        String topologyId = getTopologyIdFromApplication(CURRENT_APPLICATION.getId());
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().get("/rest/topologies/" + topologyId));
        TopologyDTO createdTopology = JsonUtil.read(Context.getInstance().getRestResponse(), TopologyDTO.class).getData();

        // base topology template
        authSteps.I_am_authenticated_with_role("ARCHITECT"); // quick win solution
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().get("/rest/topologies/" + template.getTopologyId()));
        TopologyDTO topologyTemplateBase = JsonUtil.read(Context.getInstance().getRestResponse(), TopologyDTO.class).getData();

        Map<String, NodeTemplate> nodeTemplates = topologyTemplateBase.getTopology().getNodeTemplates();

        // node templates count test
        assertEquals(createdTopology.getTopology().getNodeTemplates().keySet().size(), nodeTemplates.keySet().size());

        // node templates name / type test
        for (Map.Entry<String, NodeTemplate> entry : createdTopology.getTopology().getNodeTemplates().entrySet()) {
            assertTrue(nodeTemplates.containsKey(entry.getKey()));
            assertTrue(nodeTemplates.get(entry.getKey()).getType().equals(entry.getValue().getType()));
        }

    }

    @When("^I create a new application with name \"([^\"]*)\" and description \"([^\"]*)\" without errors$")
    public void I_create_a_new_application_with_name_and_description_without_errors(String name, String description) throws Throwable {
        I_create_a_new_application_with_name_and_description(name, description);
        commonStepDefinitions.I_should_receive_a_RestResponse_with_no_error();
    }

    @When("^I retrieve the newly created application$")
    public void I_retrieve_the_newly_created_application() throws Throwable {
        // App from context
        Application contextApp = Context.getInstance().getApplication();
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().get("/rest/applications/" + contextApp.getId()));
    }

    @Given("^There is a \"([^\"]*)\" application$")
    public void There_is_a_application(String applicationName) throws Throwable {
        SearchRequest searchRequest = new SearchRequest(null, applicationName, 0, 50, null);
        String searchResponse = Context.getRestClientInstance().postJSon("/rest/applications/search", JsonUtil.toString(searchRequest));
        RestResponse<FacetedSearchResult> response = JsonUtil.read(searchResponse, FacetedSearchResult.class);
        boolean hasApplication = false;
        for (Object appAsObj : response.getData().getData()) {
            Application app = JsonUtil.readObject(JsonUtil.toString(appAsObj), Application.class);
            if (applicationName.equals(app.getName())) {
                hasApplication = true;
                CURRENT_APPLICATION = app;
            }
        }
        if (!hasApplication) {
            I_create_a_new_application_with_name_and_description(applicationName, "");
        }
    }

    @When("^I add a tag with key \"([^\"]*)\" and value \"([^\"]*)\" to the application$")
    public void I_add_a_tag_with_key_and_value_to_the_component(String key, String value) throws Throwable {
        addTag(CURRENT_APPLICATION.getId(), key, value);
    }

    private void addTag(String applicationId, String key, String value) throws JsonProcessingException, IOException {
        UpdateTagRequest updateTagRequest = new UpdateTagRequest();
        updateTagRequest.setTagKey(key);
        updateTagRequest.setTagValue(value);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().postJSon("/rest/applications/" + applicationId + "/tags", jsonMapper.writeValueAsString(updateTagRequest)));

    }

    @Given("^There is a \"([^\"]*)\" application with tags:$")
    public void There_is_a_application_with_tags(String applicationName, DataTable tags) throws Throwable {
        // Create a new application with tags
        CreateApplicationRequest request = new CreateApplicationRequest(applicationName, "", null);
        String responseAsJson = Context.getRestClientInstance().postJSon("/rest/applications/", JsonUtil.toString(request));
        String applicationId = JsonUtil.read(responseAsJson, String.class).getData();

        // Add tags to the application
        for (List<String> rows : tags.raw()) {
            addTag(applicationId, rows.get(0), rows.get(1));
        }

        Context.getInstance().registerRestResponse(responseAsJson);
    }

    @Given("^I have an application tag \"([^\"]*)\"$")
    public boolean I_have_and_a_tag(String tag) throws Throwable {
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().get("/rest/applications/" + CURRENT_APPLICATION.getId()));
        Application application = JsonUtil.read(Context.getInstance().takeRestResponse(), Application.class).getData();
        assertTrue(application.getTags().contains(new Tag(tag, null)));
        return application.getTags().contains(new Tag(tag, null));
    }

    @When("^I delete an application tag with key \"([^\"]*)\"$")
    public void I_delete_a_tag_with_key(String tagId) throws Throwable {
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().delete("/rest/applications/" + CURRENT_APPLICATION.getId() + "/tags/" + tagId));
    }

    private Set<String> registeredApps = Sets.newHashSet();
    private String previousRestResponse;

    @Given("^There is (\\d+) applications indexed in ALIEN$")
    public void There_is_applications_indexed_in_ALIEN(int applicationCount) throws Throwable {
        CURRENT_APPLICATIONS.clear();
        registeredApps.clear();
        for (int i = 0; i < applicationCount; i++) {
            String appName = "name" + i;
            I_create_a_new_application_with_name_and_description(appName, "");
            registeredApps.add(appName);
            CURRENT_APPLICATIONS.put(appName, CURRENT_APPLICATION);
        }
    }

    @When("^I search applications from (\\d+) with result size of (\\d+)$")
    public void I_search_applications_from_with_result_size_of(int from, int to) throws Throwable {
        SearchRequest searchRequest = new SearchRequest(null, "", from, to, null);
        previousRestResponse = Context.getInstance().getRestResponse();
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().postJSon("/rest/applications/search", JsonUtil.toString(searchRequest)));
    }

    @Then("^The RestResponse must contain (\\d+) applications.$")
    public void The_RestResponse_must_contain_applications(int count) throws Throwable {
        RestResponse<FacetedSearchResult> response = JsonUtil.read(Context.getInstance().getRestResponse(), FacetedSearchResult.class);
        assertEquals(count, response.getData().getTotalResults());
        assertEquals(count, response.getData().getData().length);
    }

    @Then("^I should be able to view the (\\d+) other applications.$")
    public void I_should_be_able_to_view_the_other_applications(int count) throws Throwable {

        removeFromRegisteredApps(previousRestResponse);
        removeFromRegisteredApps(Context.getInstance().getRestResponse());

        assertEquals(0, registeredApps.size());
    }

    @SuppressWarnings("unchecked")
    private void removeFromRegisteredApps(String responseAsJson) throws Throwable {
        RestResponse<FacetedSearchResult> response = JsonUtil.read(responseAsJson, FacetedSearchResult.class);
        for (Object appAsObj : response.getData().getData()) {
            Map<String, Object> appAsMap = (Map<String, Object>) appAsObj;
            registeredApps.remove(appAsMap.get("name"));
        }
    }

    private String imagePath = "../alien4cloud-core/src/main/resources/tosca-base-types/1.0/images/compute.png";

    @When("^i update its image$")
    public void i_update_its_image() throws Throwable {
        String appId = JsonUtil.read(Context.getInstance().getRestResponse(), String.class).getData();
        RestResponse<String> response = JsonUtil.read(
                Context.getRestClientInstance().postMultipart("/rest/applications/" + appId + "/image", "file", Files.newInputStream(Paths.get(imagePath))),
                String.class);
        assertNull(response.getError());
    }

    @Then("^the application can be found in ALIEN$")
    public Application the_application_can_be_found_in_ALIEN() throws Throwable {
        String appId = CURRENT_APPLICATION.getId();
        RestResponse<Application> response = JsonUtil.read(Context.getRestClientInstance().get("/rest/applications/" + appId), Application.class);
        assertNotNull(response.getData());
        return response.getData();
    }

    @Then("^the application can be found in ALIEN with its new image$")
    public void the_application_can_be_found_in_ALIEN_with_its_new_image() throws Throwable {
        Application app = the_application_can_be_found_in_ALIEN();
        assertNotNull(app.getImageId());
    }

    @Given("^I have an application with name \"([^\"]*)\"$")
    public void I_have_an_application_with_name(String appName) throws Throwable {
        I_create_a_new_application_with_name_and_description(appName, "Default application description...");
    }

    @When("^I add a role \"([^\"]*)\" to user \"([^\"]*)\" on the application \"([^\"]*)\"$")
    public void I_add_a_role_to_user_on_the_application(String role, String username, String applicationName) throws Throwable {
        I_search_for_application(applicationName);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().put("/rest/applications/" + CURRENT_APPLICATION.getId() + "/userRoles/" + username + "/" + role));
    }

    @When("^I search for \"([^\"]*)\" application$")
    public void I_search_for_application(String applicationName) throws Throwable {
        SearchRequest searchRequest = new SearchRequest(null, applicationName, 0, 10, null);
        String searchResponse = Context.getRestClientInstance().postJSon("/rest/applications/search", JsonUtil.toString(searchRequest));
        Context.getInstance().registerRestResponse(searchResponse);
        RestResponse<FacetedSearchResult> response = JsonUtil.read(searchResponse, FacetedSearchResult.class);
        for (Object appAsObj : response.getData().getData()) {
            Application app = JsonUtil.readObject(JsonUtil.toString(appAsObj), Application.class);
            if (applicationName.equals(app.getName())) {
                CURRENT_APPLICATION = app;
            }
        }
    }

    @Then("^The application should have a user \"([^\"]*)\" having \"([^\"]*)\" role$")
    public void The_application_should_have_a_user_having_role(String username, String expectedRole) throws Throwable {
        assertNotNull(CURRENT_APPLICATION);
        assertNotNull(CURRENT_APPLICATION.getUserRoles());
        Set<String> userRoles = CURRENT_APPLICATION.getUserRoles().get(username);
        assertNotNull(userRoles);
        assertTrue(userRoles.contains(expectedRole));

    }

    @Given("^there is a user \"([^\"]*)\" with the \"([^\"]*)\" role on the application \"([^\"]*)\"$")
    public void there_is_a_user_with_the_role_on_the_application(String username, String expectedRole, String applicationName) throws Throwable {
        authSteps.There_is_a_user_in_the_system(username);
        I_search_for_application(applicationName);
        Map<String, Set<String>> userRoles = CURRENT_APPLICATION.getUserRoles();
        if (userRoles != null && userRoles.containsKey(username)) {
            if (userRoles.get(username) != null && userRoles.get(username).contains(expectedRole)) {
                return;
            }
        }

        I_add_a_role_to_user_on_the_application(expectedRole, username, applicationName);
    }

    @Given("^there is a user \"([^\"]*)\" with the following roles on the application \"([^\"]*)\"$")
    public void there_is_a_user_with_the_following_roles_on_the_application(String username, String applicationName, List<String> expectedRoles)
            throws Throwable {

        for (String expectedRole : expectedRoles) {
            there_is_a_user_with_the_role_on_the_application(username, expectedRole, applicationName);
        }
    }

    @When("^I remove a role \"([^\"]*)\" to user \"([^\"]*)\" on the application \"([^\"]*)\"$")
    public void I_remove_a_role_to_user_on_the_application(String role, String username, String applicationName) throws Throwable {
        I_search_for_application(applicationName);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().delete("/rest/applications/" + CURRENT_APPLICATION.getId() + "/userRoles/" + username + "/" + role));
    }

    @Then("^The application should have a user \"([^\"]*)\" not having \"([^\"]*)\" role$")
    public void The_application_should_have_a_user_not_having_role(String username, String expectedRole) throws Throwable {
        if (CURRENT_APPLICATION != null && CURRENT_APPLICATION.getUserRoles() != null) {
            Set<String> userRoles = CURRENT_APPLICATION.getUserRoles().get(username);
            assertNotNull(userRoles);
            if (userRoles != null) {
                assertFalse(userRoles.contains(expectedRole));
            }
        }
    }

    @When("^I delete the application \"([^\"]*)\"$")
    public void I_delete_the_application(String applicationName) throws Throwable {
        String id = CURRENT_APPLICATION.getName().equals(applicationName) ? CURRENT_APPLICATION.getId() : CURRENT_APPLICATIONS.get(applicationName).getId();
        Context.getInstance().registerRestResponse(Context.getRestClientInstance().delete("/rest/applications/" + id));
    }

    @Then("^the application should not be found$")
    public Application the_application_should_not_be_found() throws Throwable {
        RestResponse<Application> response = JsonUtil.read(Context.getRestClientInstance().get("/rest/applications/" + CURRENT_APPLICATION.getId()),
                Application.class);
        assertNull(response.getData());
        return response.getData();
    }

    @Given("^I have an applications with names and descriptions$")
    public void I_have_an_applications_with_names(DataTable applicationNames) throws Throwable {
        CURRENT_APPLICATIONS.clear();
        // Create each application and store in CURRENT_APPS
        for (List<String> app : applicationNames.raw()) {
            CreateApplicationRequest request = new CreateApplicationRequest(app.get(0), app.get(1), null);
            Context.getInstance().registerRestResponse(Context.getRestClientInstance().postJSon("/rest/applications/", JsonUtil.toString(request)));
            RestResponse<String> reponse = JsonUtil.read(Context.getInstance().getRestResponse(), String.class);
            String applicationJson = Context.getRestClientInstance().get("/rest/applications/" + reponse.getData());
            RestResponse<Application> application = JsonUtil.read(applicationJson, Application.class);
            CURRENT_APPLICATIONS.put(app.get(0), application.getData());
        }

        assertEquals(CURRENT_APPLICATIONS.size(), applicationNames.raw().size());

    }

    @Given("^I add a role \"([^\"]*)\" to group \"([^\"]*)\" on the application \"([^\"]*)\"$")
    public void I_add_a_role_to_group_on_the_application(String role, String groupName, String applicationName) throws Throwable {
        I_search_for_application(applicationName);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().put(
                        "/rest/applications/" + CURRENT_APPLICATION.getId() + "/groupRoles/" + Context.getInstance().getGroupId(groupName) + "/" + role));
    }

    @And("^The application should have a group \"([^\"]*)\" having \"([^\"]*)\" role$")
    public void The_application_should_have_a_group_having_role(String groupName, String role) throws Throwable {
        assertNotNull(CURRENT_APPLICATION);
        assertNotNull(CURRENT_APPLICATION.getGroupRoles());
        Set<String> groupRoles = CURRENT_APPLICATION.getGroupRoles().get(Context.getInstance().getGroupId(groupName));
        assertNotNull(groupRoles);
        assertTrue(groupRoles.contains(role));
    }

    @And("^There is a group \"([^\"]*)\" with the following roles on the application \"([^\"]*)\"$")
    public void There_is_a_group_with_the_following_roles_on_the_application(String groupName, String applicationName, List<String> expectedRoles)
            throws Throwable {
        for (String expectedRole : expectedRoles) {
            I_add_a_role_to_group_on_the_application(expectedRole, groupName, applicationName);
        }
    }

    @When("^I remove a role \"([^\"]*)\" from group \"([^\"]*)\" on the application \"([^\"]*)\"$")
    public void I_remove_a_role_from_group_on_the_application(String role, String groupName, String applicationName) throws Throwable {
        I_search_for_application(applicationName);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().delete(
                        "/rest/applications/" + CURRENT_APPLICATION.getId() + "/groupRoles/" + Context.getInstance().getGroupId(groupName) + "/" + role));
    }

    @And("^The application should have the group \"([^\"]*)\" not having \"([^\"]*)\" role$")
    public void The_application_should_have_the_group_not_having_role(String groupName, String role) throws Throwable {
        if (CURRENT_APPLICATION.getGroupRoles() != null) {
            Set<String> groupRoles = CURRENT_APPLICATION.getGroupRoles().get(groupName);
            if (groupRoles != null) {
                assertFalse(groupRoles.contains(role));
            }
        }
    }

    @And("^The application should have the group \"([^\"]*)\" having \"([^\"]*)\" role$")
    public void The_application_should_have_the_group_having_role(String groupName, String role) throws Throwable {
        assertNotNull(CURRENT_APPLICATION.getGroupRoles());
        Set<String> groupRoles = CURRENT_APPLICATION.getGroupRoles().get(Context.getInstance().getGroupId(groupName));
        assertNotNull(groupRoles);
        assertTrue(groupRoles.contains(role));
    }

    @And("^The RestResponse must contain these applications$")
    public void The_RestResponse_must_contain_these_applications(List<String> expectedApplications) throws Throwable {
        RestResponse<FacetedSearchResult> response = JsonUtil.read(Context.getInstance().getRestResponse(), FacetedSearchResult.class);
        assertNotNull(response.getData());
        assertEquals(expectedApplications.size(), response.getData().getTotalResults());
        assertEquals(expectedApplications.size(), response.getData().getData().length);
        Set<String> actualApplications = Sets.newHashSet();
        for (Object appObj : response.getData().getData()) {
            actualApplications.add(((Map) appObj).get("name").toString());
        }
        assertEquals(Sets.newHashSet(expectedApplications), actualApplications);
    }

    @Then("^I should receive an application without \"([^\"]*)\" as user$")
    public void I_should_receive_an_application_without_as_user(String userName) throws Throwable {
        RestResponse<Application> response = JsonUtil.read(Context.getInstance().getRestResponse(), Application.class);
        Assert.assertNull(response.getError());
        Assert.assertNotNull(response.getData());
        Application application = response.getData();
        if (application.getUserRoles() != null) {
            Assert.assertFalse(application.getUserRoles().containsKey(userName));
        }
    }

    @When("^I set the \"([^\"]*)\" of this application to \"([^\"]*)\"$")
    public void I_set_the_of_this_application_to(String fieldName, String fieldValue) throws Throwable {
        Map<String, Object> request = Maps.newHashMap();
        request.put(fieldName, fieldValue);
        Context.getInstance().registerRestResponse(
                Context.getRestClientInstance().putJSon("/rest/applications/" + CURRENT_APPLICATION.getId(), JsonUtil.toString(request)));
        ReflectionUtil.setPropertyValue(CURRENT_APPLICATION, fieldName, fieldValue);
    }

    @And("^The application can be found in ALIEN with its \"([^\"]*)\" set to \"([^\"]*)\"$")
    public void The_application_can_be_found_in_ALIEN_with_its_set_to(String fieldName, String fieldValue) throws Throwable {
        Application application = the_application_can_be_found_in_ALIEN();
        Assert.assertEquals(fieldValue, ReflectionUtil.getPropertyValue(application, fieldName).toString());
    }
}