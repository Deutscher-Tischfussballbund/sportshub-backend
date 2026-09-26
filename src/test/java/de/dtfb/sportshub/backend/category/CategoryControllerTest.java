package de.dtfb.sportshub.backend.category;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.support.TestIds;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Category name/short name are mandatory, and the short name is unique (case-insensitive). */
class CategoryControllerTest extends AuthorizedControllerTest {

    @Test
    void create_trimsAndStoresEligibleSide() throws Exception {
        String shortName = TestIds.unique("U16W");
        create("  U16 Damen ", " " + shortName + " ", "women")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("U16 Damen"))
            .andExpect(jsonPath("$.shortName").value(shortName))
            .andExpect(jsonPath("$.eligibleSide").value("women"));
    }

    @Test
    void create_duplicateShortName_caseInsensitive_isConflict() throws Exception {
        String shortName = TestIds.unique("dup");
        create("First", shortName, null).andExpect(status().isCreated());

        create("Second", shortName.toUpperCase(), null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_SHORT_NAME_TAKEN"));
    }

    @Test
    void create_missingNameOrShortName_isBadRequest() throws Exception {
        create("Name", " ", null).andExpect(status().isBadRequest());
        create("", TestIds.unique("X"), null).andExpect(status().isBadRequest());
    }

    @Test
    void update_keepingOwnShortName_isAllowed_butTakingAnothersIsConflict() throws Exception {
        String taken = TestIds.unique("taken");
        create("Taken", taken, null).andExpect(status().isCreated());
        String own = TestIds.unique("own");
        String id = JsonPath.read(create("Own", own, null).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), "$.id");

        update(id, "Own renamed", own).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Own renamed"));
        update(id, "Own", taken)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CATEGORY_SHORT_NAME_TAKEN"));
    }

    private ResultActions create(String name, String shortName, String eligibleSide) throws Exception {
        return mockMvc.perform(post("/v1/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"name\": \"%s\", \"shortName\": \"%s\", \"eligibleSide\": %s}",
                name, shortName, eligibleSide == null ? "null" : "\"" + eligibleSide + "\"")));
    }

    private ResultActions update(String id, String name, String shortName) throws Exception {
        return mockMvc.perform(put("/v1/categories/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"name\": \"%s\", \"shortName\": \"%s\"}", name, shortName)));
    }
}
