package com.meridian.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PageResponse} — the ubiquitous paging envelope every
 * paged controller returns. Ensures the record's Page adapter does not
 * silently drop fields clients rely on (content, page, size, total).
 */
class PageResponseTest {

    @Test
    void from_populatedPage_preservesContentAndPaging() {
        Page<String> page = new PageImpl<>(List.of("a", "b", "c"), PageRequest.of(1, 3), 25);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b", "c");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.total()).isEqualTo(25);
    }

    @Test
    void from_emptyPage_returnsEmptyContentWithZeroTotal() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(0);
    }

    @Test
    void from_lastPartialPage_reportsRequestedSizeNotContentSize() {
        // Page 2 with requested size 10 but only 4 items left — the envelope
        // reports the REQUESTED size so clients render consistent pagination.
        Page<String> page = new PageImpl<>(List.of("x", "y", "z", "w"), PageRequest.of(2, 10), 24);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).hasSize(4);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.total()).isEqualTo(24);
    }

    @Test
    void directConstructor_buildsEnvelope() {
        PageResponse<Integer> envelope = new PageResponse<>(List.of(1, 2), 0, 50, 2L);

        assertThat(envelope.content()).containsExactly(1, 2);
        assertThat(envelope.page()).isEqualTo(0);
        assertThat(envelope.size()).isEqualTo(50);
        assertThat(envelope.total()).isEqualTo(2L);
    }
}
