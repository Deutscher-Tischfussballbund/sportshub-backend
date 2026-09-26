package de.dtfb.sportshub.backend.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The generated OpenAPI document reflects the {@link OpenApiConfig} customizations: auth schemes and
 * prettified tag names, plus the shared {@link de.dtfb.sportshub.backend.exception.ApiError} error
 * responses springdoc derives from {@code GlobalExceptionHandler}. {@code /v3/api-docs} is public
 * (see {@code SecurityConfig}), so no token is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesBothAuthSchemes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.keycloak.type").value("oauth2"));
    }

    @Test
    void exposesApiKeyScheme_onlyOnOperationsAKeyMayCall() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.api-key.type").value("apiKey"))
            .andExpect(jsonPath("$.components.securitySchemes.api-key.in").value("header"))
            .andExpect(jsonPath("$.components.securitySchemes.api-key.name").value("X-API-Key"))
            // A domain read: token OR key.
            .andExpect(jsonPath("$.paths['/v1/seasons'].get.security[*].api-key").isNotEmpty())
            // A write and an admin read: no key offered (inherit the global token-only requirement).
            .andExpect(jsonPath("$.paths['/v1/seasons'].post.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/v1/admin/players'].get.security").doesNotExist())
            .andExpect(jsonPath("$.security[*].api-key").isEmpty());
    }

    @Test
    void documents401OnSecuredOperations_butNotOnThePublicTracker() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/v1/seasons'].get.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/v1/seasons/{id}'].delete.responses['401'].content[*].schema['$ref']")
                .value(hasItem(containsString("ApiError"))))
            .andExpect(jsonPath("$.paths['/v1/tracker/issues'].get.responses['401']").doesNotExist());
    }

    @Test
    void documentsSharedErrorResponsesWithApiErrorBody() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.schemas.ApiError").exists())
            // Every common error code is documented on an operation...
            .andExpect(jsonPath("$.paths['/v1/category'].get.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/v1/category'].get.responses['403']").exists())
            .andExpect(jsonPath("$.paths['/v1/category'].get.responses['404']").exists())
            .andExpect(jsonPath("$.paths['/v1/category'].get.responses['500']").exists())
            // ...all sharing the same ApiError body.
            .andExpect(jsonPath("$.paths['/v1/category'].get.responses['403'].content[*].schema['$ref']")
                .value(hasItem(containsString("ApiError"))));
    }

    @Test
    void groupsAreOrderedAlongTheDomainChain() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        java.util.List<String> tags = com.jayway.jsonpath.JsonPath.read(json, "$.tags[*].name");

        org.junit.jupiter.api.Assertions.assertEquals("Federation", tags.get(0), "chain starts at Federation");
        // Relative order down the chain (robust to new controllers being appended).
        org.junit.jupiter.api.Assertions.assertTrue(
            tags.indexOf("Federation") < tags.indexOf("Season")
                && tags.indexOf("Season") < tags.indexOf("League")
                && tags.indexOf("League") < tags.indexOf("Tier")
                && tags.indexOf("Tier") < tags.indexOf("Group")
                && tags.indexOf("Group") < tags.indexOf("Match Event"),
            "tag order should follow the domain chain, was: " + tags);
    }

    @Test
    void prettifiesControllerTagNames() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/v1/category'].get.tags").value(hasItem("Category")))
            // No raw "*-controller" tag survives on the operation.
            .andExpect(jsonPath("$.paths['/v1/category'].get.tags[?(@ =~ /.*-controller/)]").value(empty()));
    }

    @Test
    void groupsCarryDescriptions() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags[?(@.name=='Federation')].description")
                .value(hasItem(containsString("federation tree"))))
            .andExpect(jsonPath("$.tags[?(@.name=='Role Catalog')].description")
                .value(hasItem(containsString("catalog of roles"))));
    }
}
