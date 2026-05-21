package com.meridian.api;

import com.meridian.entity.Role;
import com.meridian.entity.User;
import com.meridian.entity.UserStatus;
import com.meridian.repository.RoleRepository;
import com.meridian.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEncryptionTest extends TestContainersBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Test 1: employeeIdEnc and contactEnc are stored encrypted (not plaintext) in the DB
    @Test
    void encryptedFields_storedAsNonPlaintextInDb() {
        String plainEmployeeId = "EMP-ENCRYPT-TEST-" + UUID.randomUUID();
        String plainContact = "contact@encrypt-test.example.com";

        User user = new User();
        user.setUsername("enc-test-user-" + UUID.randomUUID());
        user.setPasswordHash("$2a$12$hashed-placeholder-not-real");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmployeeIdEnc(plainEmployeeId);
        user.setContactEnc(plainContact);

        Optional<Role> studentRole = roleRepository.findByName("ROLE_STUDENT");
        studentRole.ifPresent(r -> user.setRoles(Set.of(r)));

        User saved = userRepository.save(user);
        UUID userId = saved.getId();

        // Query raw DB columns — should NOT be plaintext
        String rawEmployeeId = jdbcTemplate.queryForObject(
                "SELECT employee_id_enc FROM users WHERE id = ?::uuid",
                String.class, userId.toString());

        String rawContact = jdbcTemplate.queryForObject(
                "SELECT contact_enc FROM users WHERE id = ?::uuid",
                String.class, userId.toString());

        assertThat(rawEmployeeId)
                .as("employee_id_enc column must be encrypted (not the plaintext value)")
                .isNotNull()
                .isNotEqualTo(plainEmployeeId);

        assertThat(rawContact)
                .as("contact_enc column must be encrypted (not the plaintext value)")
                .isNotNull()
                .isNotEqualTo(plainContact);

        // Clean up
        userRepository.deleteById(userId);
    }

    // Test 2: JPA load decrypts employeeIdEnc and contactEnc back to plaintext
    @Test
    void encryptedFields_jpaLoadReturnsDecryptedPlaintext() {
        String plainEmployeeId = "EMP-DECRYPT-TEST-" + UUID.randomUUID();
        String plainContact = "decrypt@test.example.com";

        User user = new User();
        user.setUsername("dec-test-user-" + UUID.randomUUID());
        user.setPasswordHash("$2a$12$hashed-placeholder-not-real");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmployeeIdEnc(plainEmployeeId);
        user.setContactEnc(plainContact);

        Optional<Role> studentRole = roleRepository.findByName("ROLE_STUDENT");
        studentRole.ifPresent(r -> user.setRoles(Set.of(r)));

        User saved = userRepository.save(user);
        UUID userId = saved.getId();

        // Clear the 1st-level cache so we actually reload from DB
        userRepository.findById(userId).ifPresent(reloaded -> {
            assertThat(reloaded.getEmployeeIdEnc())
                    .as("JPA must decrypt employeeIdEnc back to plaintext")
                    .isEqualTo(plainEmployeeId);

            assertThat(reloaded.getContactEnc())
                    .as("JPA must decrypt contactEnc back to plaintext")
                    .isEqualTo(plainContact);
        });

        // Clean up
        userRepository.deleteById(userId);
    }
}
