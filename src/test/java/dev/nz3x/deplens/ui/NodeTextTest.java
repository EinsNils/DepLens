package dev.nz3x.deplens.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NodeTextTest {

    @Test
    void combinesEntryAndSecurityTooltip() {
        assertThat(NodeText.combineTooltips("Versionskonflikt: 1.0 -> 2.0", "Keine bekannten Sicherheitslücken"))
                .isEqualTo("<html>Versionskonflikt: 1.0 -&gt; 2.0<br>Keine bekannten Sicherheitslücken</html>");
    }

    @Test
    void singleTooltipStaysPlain() {
        assertThat(NodeText.combineTooltips(null, "Prüfung fehlgeschlagen: x")).isEqualTo("Prüfung fehlgeschlagen: x");
        assertThat(NodeText.combineTooltips("Versionskonflikt: a", null)).isEqualTo("Versionskonflikt: a");
        assertThat(NodeText.combineTooltips(null, null)).isNull();
    }
}
