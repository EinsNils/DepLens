package io.github.einsnils.deplens.ui;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import io.github.einsnils.deplens.search.SearchHit;
import javax.swing.JList;
import org.jetbrains.annotations.NotNull;

final class SearchHitCellRenderer extends ColoredListCellRenderer<SearchHit> {

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends SearchHit> list, SearchHit hit, int index,
                                         boolean selected, boolean hasFocus) {
        NodeText.append(this, hit.node(), false);
        append("   " + hit.location(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setToolTipText(NodeText.tooltip(hit.node()));
    }
}
