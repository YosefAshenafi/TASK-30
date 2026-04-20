package com.meridian.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "ValidPass1!",        // 10 chars (too short)
        "OnlyLetters!",       // no digit
        "OnlyDigits1234",     // no symbol
        "Short1!",            // too short
        "",                   // empty
    })
    void rejectsInvalidPasswords(String password) {
        assertThat(PasswordPolicy.isValid(password)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ValidPass123!",      // exactly 12 chars, has digit + symbol
        "LongSecure#Pass99",  // 17 chars
        "my-P@ssw0rd-2024",   // dashes count as symbol
    })
    void acceptsValidPasswords(String password) {
        assertThat(PasswordPolicy.isValid(password)).isTrue();
    }

    @Test
    void validateThrowsOnMissingDigit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("NoDigits!NoDigits"))
            .isInstanceOf(PasswordPolicy.PasswordPolicyException.class)
            .hasMessageContaining("digit");
    }

    @Test
    void validateThrowsOnMissingSymbol() {
        assertThatThrownBy(() -> PasswordPolicy.validate("NoSymbol12345678"))
            .isInstanceOf(PasswordPolicy.PasswordPolicyException.class)
            .hasMessageContaining("symbol");
    }

    @Test
    void validateThrowsOnTooShort() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Sh0rt!"))
            .isInstanceOf(PasswordPolicy.PasswordPolicyException.class)
            .hasMessageContaining("12");
    }

    @Test
    void validatePassesForCompliantPassword() {
        assertThatNoException().isThrownBy(() -> PasswordPolicy.validate("Meridian#2026!"));
    }

    @Test
    void validateThrowsOnNull() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
            .isInstanceOf(PasswordPolicy.PasswordPolicyException.class);
    }
}
