package com.meridian.governance;

import com.meridian.security.entity.AllowedIpRange;
import com.meridian.security.repository.AllowedIpRangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Direct unit test for {@link AllowedIpRangeController}.
 *
 * Mocks the repository and invokes controller methods directly — this is a
 * pure controller-level test, orthogonal to the MockMvc HTTP test and the
 * real-HTTP TrueNoMockHttpApiTest. It verifies the controller's orchestration
 * (what fields it copies onto the entity, which HTTP status it returns,
 * which branch it takes on missing records) without the Spring Security
 * filter chain in the loop.
 */
class AllowedIpRangeControllerUnitTest {

    private AllowedIpRangeRepository repo;
    private AllowedIpRangeController controller;
    private Authentication adminAuth;
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

    @BeforeEach
    void setUp() {
        repo = mock(AllowedIpRangeRepository.class);
        controller = new AllowedIpRangeController(repo);
        adminAuth = new UsernamePasswordAuthenticationToken(ADMIN_ID.toString(), null, List.of());
    }

    @Test
    void list_returnsMappedDtosInOrder() {
        AllowedIpRange a = new AllowedIpRange();
        a.setId(UUID.randomUUID());
        a.setCidr("10.0.0.0/8");
        a.setRoleScope("ADMIN");
        a.setNote("corp VPN");
        AllowedIpRange b = new AllowedIpRange();
        b.setId(UUID.randomUUID());
        b.setCidr("192.168.0.0/16");
        b.setNote(null);
        when(repo.findAll()).thenReturn(List.of(a, b));

        ResponseEntity<List<AllowedIpRangeController.AllowedIpRangeDto>> res = controller.list();

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).hasSize(2);
        assertThat(res.getBody().get(0).cidr()).isEqualTo("10.0.0.0/8");
        assertThat(res.getBody().get(0).roleScope()).isEqualTo("ADMIN");
        assertThat(res.getBody().get(0).note()).isEqualTo("corp VPN");
        assertThat(res.getBody().get(1).cidr()).isEqualTo("192.168.0.0/16");
    }

    @Test
    void create_populatesEntityFromRequestAndStampsCreator() {
        AllowedIpRangeController.CreateIpRangeRequest req =
                new AllowedIpRangeController.CreateIpRangeRequest("203.0.113.0/24", "CORPORATE_MENTOR", "branch office");
        when(repo.save(any(AllowedIpRange.class))).thenAnswer(inv -> {
            AllowedIpRange saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ResponseEntity<AllowedIpRangeController.AllowedIpRangeDto> res = controller.create(req, adminAuth);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<AllowedIpRange> captor = ArgumentCaptor.forClass(AllowedIpRange.class);
        verify(repo).save(captor.capture());
        AllowedIpRange saved = captor.getValue();
        assertThat(saved.getCidr()).isEqualTo("203.0.113.0/24");
        assertThat(saved.getRoleScope()).isEqualTo("CORPORATE_MENTOR");
        assertThat(saved.getNote()).isEqualTo("branch office");
        assertThat(saved.getCreatedBy()).isEqualTo(ADMIN_ID);
        // DTO in the response mirrors the saved entity
        assertThat(res.getBody().cidr()).isEqualTo("203.0.113.0/24");
    }

    @Test
    void delete_existingId_returns204AndInvokesRepository() {
        UUID id = UUID.randomUUID();
        when(repo.existsById(id)).thenReturn(true);

        ResponseEntity<Void> res = controller.delete(id);

        assertThat(res.getStatusCode().value()).isEqualTo(204);
        verify(repo).deleteById(id);
    }

    @Test
    void delete_missingId_throws404() {
        UUID id = UUID.randomUUID();
        when(repo.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> controller.delete(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("IP range not found");
        verify(repo, never()).deleteById(eq(id));
    }

    @Test
    void dtoFrom_copiesAllFields() {
        AllowedIpRange r = new AllowedIpRange();
        UUID id = UUID.randomUUID();
        r.setId(id);
        r.setCidr("172.16.0.0/12");
        r.setRoleScope("FACULTY_MENTOR");
        r.setNote("hospital");

        AllowedIpRangeController.AllowedIpRangeDto dto = AllowedIpRangeController.AllowedIpRangeDto.from(r);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.cidr()).isEqualTo("172.16.0.0/12");
        assertThat(dto.roleScope()).isEqualTo("FACULTY_MENTOR");
        assertThat(dto.note()).isEqualTo("hospital");
    }
}
