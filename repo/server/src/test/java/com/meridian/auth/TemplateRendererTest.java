package com.meridian;

import com.meridian.notifications.TemplateRenderer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for notification template XSS hardening.
 *
 * Before sanitisation, user-provided variable values (e.g. display names,
 * free-form notes) were substituted into markdown templates and then passed
 * unfiltered to flexmark. If a value contained a raw HTML tag, it would be
 * preserved verbatim in the rendered HTML, giving an attacker a vector to
 * inject script markup into admin or learner inboxes.
 */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void renderToHtml_escapesScriptTagsInVariableValues() {
        String template = "Hello {{name}}";
        Map<String, String> vars = Map.of("name", "<script>alert(1)</script>");

        String html = renderer.renderToHtml(template, vars);

        assertFalse(html.toLowerCase().contains("<script"),
                "rendered HTML must not contain a raw <script> tag: " + html);
        assertTrue(html.contains("&lt;script"),
                "rendered HTML should contain HTML-escaped script tag: " + html);
    }

    @Test
    void renderToHtml_escapesEventHandlerAttributes() {
        String template = "Image: {{alt}}";
        Map<String, String> vars = Map.of("alt", "\" onerror=\"alert(1)");

        String html = renderer.renderToHtml(template, vars);

        // Ensure no HTML tag contains a raw event-handler attribute.
        assertFalse(html.matches("(?is).*<[^>]+\\son\\w+\\s*=.*"),
                "rendered HTML must not contain raw event-handler attributes: " + html);
    }

    @Test
    void render_plainTextPassthroughDoesNotEscape() {
        // The plain-text render() path should not double-escape — it just
        // substitutes variables verbatim for downstream callers that do
        // their own handling.
        String out = renderer.render("Hi {{name}}", Map.of("name", "<b>Ada</b>"));
        assertEquals("Hi <b>Ada</b>", out);
    }
}
