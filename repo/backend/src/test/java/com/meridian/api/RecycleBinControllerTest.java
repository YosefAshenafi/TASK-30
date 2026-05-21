package com.meridian.api;

import com.meridian.entity.RecycleBinEntry;
import com.meridian.repository.RecycleBinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecycleBinControllerTest extends TestContainersBase {

    private static final String BASE_URL = "/api/admin/recycle-bin";

    // Seeded admin user id from V2 migration
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("33333333-0000-0000-0000-000000000001");

    @Autowired
    private RecycleBinRepository recycleBinRepository;

    private String adminToken;
    private String studentToken;
    private UUID seededEntryId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        studentToken = loginAs("student1", "Student@12345678");

        // Seed a recycle bin entry for restore/delete tests
        RecycleBinEntry entry = new RecycleBinEntry();
        entry.setEntityType("Course");
        entry.setEntityId(UUID.fromString("44444444-0000-0000-0000-000000000001"));
        entry.setOriginalData("{\"title\":\"Test Course\"}");
        entry.setDeletedBy(ADMIN_USER_ID);
        entry.setExpiresAt(Instant.now().plusSeconds(86400 * 14));
        RecycleBinEntry saved = recycleBinRepository.save(entry);
        seededEntryId = saved.getId();
    }

    // Test 1: GET /api/admin/recycle-bin as ADMIN → 200
    @Test
    void list_asAdmin_returns200() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 2: GET /api/admin/recycle-bin as STUDENT → 403
    @Test
    void list_asStudent_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 3: POST /api/admin/recycle-bin/{id}/restore as ADMIN with valid id → 200
    @Test
    void restore_asAdmin_validId_returns200() throws Exception {
        // Seed a fresh entry for this test to avoid state leakage from other tests
        RecycleBinEntry entry = new RecycleBinEntry();
        entry.setEntityType("User");
        entry.setEntityId(UUID.randomUUID());
        entry.setOriginalData("{\"username\":\"deleted_user\"}");
        entry.setDeletedBy(ADMIN_USER_ID);
        entry.setExpiresAt(Instant.now().plusSeconds(86400 * 14));
        RecycleBinEntry saved = recycleBinRepository.save(entry);

        mockMvc.perform(post(BASE_URL + "/" + saved.getId() + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 4: DELETE /api/admin/recycle-bin/{id} as ADMIN → 204
    @Test
    void hardDelete_asAdmin_returns204() throws Exception {
        // Seed a fresh entry to delete
        RecycleBinEntry entry = new RecycleBinEntry();
        entry.setEntityType("Session");
        entry.setEntityId(UUID.randomUUID());
        entry.setOriginalData("{\"sessionId\":\"test\"}");
        entry.setDeletedBy(ADMIN_USER_ID);
        entry.setExpiresAt(Instant.now().plusSeconds(86400 * 14));
        RecycleBinEntry saved = recycleBinRepository.save(entry);

        mockMvc.perform(delete(BASE_URL + "/" + saved.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    // Test 5: GET /api/admin/recycle-bin unauthenticated → 401
    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }
}
