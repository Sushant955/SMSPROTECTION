package com.ucbps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {
    @Autowired MockMvc mvc;

    private String register(String email, boolean consent) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Passw0rd!x\",\"country\":\"IN\",\"reportingConsent\":" + consent + "}";
        String res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(res).get("token").asText();
    }

    private String report(String hash) {
        return "{\"numberHash\":\"" + hash + "\",\"country\":\"IN\",\"type\":\"OTP_FLOOD\",\"riskScore\":0.9}";
    }

    @Test
    void reportsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON).content(report("a".repeat(64))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportingRequiresConsent() throws Exception {
        String token = register("noconsent@example.test", false);
        mvc.perform(post("/api/v1/reports").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(report("b".repeat(64))))
                .andExpect(status().isForbidden());
    }

    @Test
    void numberIsFlaggedOnlyAfterThreeIndependentReporters() throws Exception {
        String hash = "c".repeat(64);
        for (int i = 1; i <= 3; i++) {
            String token = register("reporter" + i + "@example.test", true);
            mvc.perform(post("/api/v1/reports").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(report(hash)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.flagged").value(i >= 3));
        }
        String viewer = register("viewer@example.test", false);
        mvc.perform(get("/api/v1/intel/reputation/" + hash).header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flagged").value(true));
    }

    @Test
    void normalUserCannotOpenAdminEndpoints() throws Exception {
        String token = register("plain@example.test", true);
        mvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
