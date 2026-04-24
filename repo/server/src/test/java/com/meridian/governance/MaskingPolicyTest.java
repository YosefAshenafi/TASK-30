package com.meridian.governance;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingPolicyTest {

    private final MaskingPolicy policy = new MaskingPolicy();

    @Test
    void canUnmask_admin_alwaysTrue() {
        assertThat(policy.canUnmask("ADMIN", UUID.randomUUID(), null,
                UUID.randomUUID(), null)).isTrue();
    }

    @Test
    void canUnmask_faculty_alwaysTrue() {
        assertThat(policy.canUnmask("FACULTY_MENTOR", UUID.randomUUID(), null,
                UUID.randomUUID(), null)).isTrue();
    }

    @Test
    void canUnmask_selfViewing_true() {
        UUID me = UUID.randomUUID();
        assertThat(policy.canUnmask("STUDENT", me, null, me, null)).isTrue();
    }

    @Test
    void canUnmask_studentViewingOther_false() {
        assertThat(policy.canUnmask("STUDENT", UUID.randomUUID(), null,
                UUID.randomUUID(), null)).isFalse();
    }

    @Test
    void canUnmask_corporateMentorSameOrg_true() {
        UUID org = UUID.randomUUID();
        assertThat(policy.canUnmask("CORPORATE_MENTOR", UUID.randomUUID(), org,
                UUID.randomUUID(), org)).isTrue();
    }

    @Test
    void canUnmask_corporateMentorDifferentOrg_false() {
        assertThat(policy.canUnmask("CORPORATE_MENTOR", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    void maskUsername_longName_masksMiddle() {
        assertThat(policy.maskUsername("alice")).isEqualTo("a***e");
    }

    @Test
    void maskUsername_shortName_fullyMasks() {
        assertThat(policy.maskUsername("ab")).isEqualTo("**");
        assertThat(policy.maskUsername("x")).isEqualTo("*");
    }

    @Test
    void maskUsername_null_returnsNull() {
        assertThat(policy.maskUsername(null)).isNull();
    }

    @Test
    void maskEmail_masksLocalPart() {
        assertThat(policy.maskEmail("alice@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void maskEmail_noAtSymbol_returnsAsterisks() {
        assertThat(policy.maskEmail("notanemail")).isEqualTo("***");
    }

    @Test
    void maskDisplayName_multipleWords_masksEach() {
        assertThat(policy.maskDisplayName("Alice Smith")).isEqualTo("A*** S***");
    }

    @Test
    void maskPhone_standard_masksMiddle() {
        assertThat(policy.maskPhone("+14155551234")).isEqualTo("+1***34");
    }

    @Test
    void maskPhone_tooShort_returnsAsterisks() {
        assertThat(policy.maskPhone("123")).isEqualTo("***");
    }

    @Test
    void maskField_dispatchesByFieldName() {
        assertThat(policy.maskField("email", "alice@x.com")).startsWith("a***");
        assertThat(policy.maskField("display_name", "Alice")).isEqualTo("A***");
        assertThat(policy.maskField("phone_number", "+14155551234")).isEqualTo("+1***34");
        assertThat(policy.maskField("unknown", "alice")).isEqualTo("a***e");
    }

    @Test
    void maskField_nullValue_returnsNull() {
        assertThat(policy.maskField("email", null)).isNull();
    }

    @Test
    void apply_whenCanUnmask_returnsOriginal() {
        assertThat(policy.apply("alice", true)).isEqualTo("alice");
    }

    @Test
    void apply_whenCannotUnmask_returnsMasked() {
        assertThat(policy.apply("alice", false)).isEqualTo("a***e");
    }
}
