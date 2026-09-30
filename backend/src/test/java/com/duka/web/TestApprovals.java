package com.duka.web;

import com.duka.domain.PlatformAdmin;
import com.duka.repo.PlatformAdminRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class TestApprovals {
    private TestApprovals() {}

    static String approve(MockMvc mvc, ObjectMapper json, PasswordEncoder encoder, PlatformAdminRepository admins, String ownerToken) throws Exception {
        long businessId = json.readTree(mvc.perform(get("/api/account/me").header("Authorization", "Bearer " + ownerToken))
                .andReturn().getResponse().getContentAsString()).get("businessId").asLong();
        String phone = "075" + String.valueOf(Math.abs(System.nanoTime())).substring(0, 7);
        PlatformAdmin admin = new PlatformAdmin();
        admin.setName("Test super admin");
        admin.setPhone(phone);
        admin.setPinHash(encoder.encode("4321"));
        admin.setRole("SUPER_ADMIN");
        admins.save(admin);
        String platform = json.readTree(mvc.perform(post("/api/platform/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"pin\":\"4321\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/platform/businesses/" + businessId + "/approve")
                        .header("Authorization", "Bearer " + platform)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Test approval\"}"))
                .andExpect(status().isOk());
        return platform;
    }
}
