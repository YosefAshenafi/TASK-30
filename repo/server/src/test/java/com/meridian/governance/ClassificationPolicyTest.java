package com.meridian.governance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationPolicyTest {

    private final ClassificationPolicy policy = new ClassificationPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "INTERNAL"})
    void canView_nonSensitive_allowsAnyRole(String classification) {
        assertThat(policy.canView(classification, "STUDENT")).isTrue();
        assertThat(policy.canView(classification, "CORPORATE_MENTOR")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "CONFIDENTIAL, ADMIN, true",
            "CONFIDENTIAL, FACULTY_MENTOR, true",
            "CONFIDENTIAL, STUDENT, false",
            "CONFIDENTIAL, CORPORATE_MENTOR, false",
            "RESTRICTED, ADMIN, true",
            "RESTRICTED, STUDENT, false",
    })
    void canView_sensitive_requiresPrivilegedRole(String classification, String role, boolean expected) {
        assertThat(policy.canView(classification, role)).isEqualTo(expected);
    }

    @Test
    void canModify_requiresAdminOrFaculty() {
        assertThat(policy.canModify("ADMIN")).isTrue();
        assertThat(policy.canModify("FACULTY_MENTOR")).isTrue();
        assertThat(policy.canModify("STUDENT")).isFalse();
        assertThat(policy.canModify("CORPORATE_MENTOR")).isFalse();
    }

    @Test
    void canModify_nullRole_returnsFalse() {
        assertThat(policy.canModify(null)).isFalse();
    }
}
