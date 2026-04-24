package com.meridian.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthPrincipal} — the Spring-Security principal adapter
 * every controller derives role + userId + orgId from. Small module, large blast
 * radius: a regression here can silently widen authorization.
 */
class AuthPrincipalTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void of_nullAuth_returnsNull() {
        assertThat(AuthPrincipal.of(null)).isNull();
    }

    @Test
    void of_authWithAuthPrincipalInstance_returnsItUnmodified() {
        AuthPrincipal raw = new AuthPrincipal(USER_ID, "ADMIN", ORG_ID);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                raw, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        AuthPrincipal result = AuthPrincipal.of(auth);

        assertThat(result).isSameAs(raw);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(result.organizationId()).isEqualTo(ORG_ID);
    }

    @Test
    void of_plainAuth_derivesRoleFromAuthorityAndStripsRolePrefix() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CORPORATE_MENTOR")));

        AuthPrincipal result = AuthPrincipal.of(auth);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.role()).isEqualTo("CORPORATE_MENTOR");
        // Plain auth tokens have no organization — controllers must handle that.
        assertThat(result.organizationId()).isNull();
    }

    @Test
    void of_plainAuthWithoutAuthorities_defaultsRoleToStudent() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null, List.of());

        AuthPrincipal result = AuthPrincipal.of(auth);

        assertThat(result.role()).isEqualTo("STUDENT");
    }

    @Test
    void staticAccessors_matchInstanceAccessors() {
        AuthPrincipal raw = new AuthPrincipal(USER_ID, "FACULTY_MENTOR", ORG_ID);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                raw, null, List.of(new SimpleGrantedAuthority("ROLE_FACULTY_MENTOR")));

        assertThat(AuthPrincipal.userId(auth)).isEqualTo(USER_ID);
        assertThat(AuthPrincipal.role(auth)).isEqualTo("FACULTY_MENTOR");
        assertThat(AuthPrincipal.orgId(auth)).isEqualTo(ORG_ID);
    }

    @Test
    void getName_returnsUserIdString() {
        AuthPrincipal p = new AuthPrincipal(USER_ID, "ADMIN", ORG_ID);
        assertThat(p.getName()).isEqualTo(USER_ID.toString());
    }
}
