package dev.nz3x.deplens.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.jetbrains.annotations.Nullable;

/** Settings → Tools → DepLens. */
public final class DepLensConfigurable implements Configurable {
    private final JBCheckBox securityEnabled = new JBCheckBox("Sicherheitsprüfung aktivieren (OSV, Maven Central)");
    private final JSpinner cacheHours = new JSpinner(new SpinnerNumberModel(24, 1, 24 * 30, 1));
    private final JBTextField ossIndexUser = new JBTextField();
    private final JBPasswordField ossIndexToken = new JBPasswordField();
    private final JBLabel tokenHint = new JBLabel();

    @Override
    public String getDisplayName() {
        return "DepLens";
    }

    @Override
    public @Nullable JComponent createComponent() {
        tokenHint.setForeground(UIUtil.getContextHelpForeground());
        return FormBuilder.createFormBuilder()
                .addComponent(securityEnabled)
                .addLabeledComponent("Cache-Dauer (Stunden):", cacheHours)
                .addSeparator()
                .addComponent(new JBLabel("Sonatype OSS Index (optional, zusätzliche Lückenquelle)"))
                .addLabeledComponent("Benutzer (E-Mail):", ossIndexUser)
                .addLabeledComponent("API-Token:", ossIndexToken)
                .addComponent(tokenHint)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        DepLensSettings.State state = DepLensSettings.getInstance().getState();
        return securityEnabled.isSelected() != state.securityEnabled
                || (int) cacheHours.getValue() != state.cacheHours
                || !Objects.equals(ossIndexUser.getText().strip(), state.ossIndexUser)
                || ossIndexToken.getPassword().length > 0;
    }

    @Override
    public void apply() {
        DepLensSettings settings = DepLensSettings.getInstance();
        DepLensSettings.State state = settings.getState();
        state.securityEnabled = securityEnabled.isSelected();
        state.cacheHours = (int) cacheHours.getValue();
        state.ossIndexUser = ossIndexUser.getText().strip();
        char[] token = ossIndexToken.getPassword();
        ossIndexToken.setText("");
        if (token.length == 0) {
            settings.notifyChanged();
            reset();
            return;
        }
        state.ossIndexTokenStored = true;
        String value = new String(token);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            settings.storeOssIndexToken(value);
            ApplicationManager.getApplication().invokeLater(settings::notifyChanged);
        });
        reset();
    }

    @Override
    public void reset() {
        DepLensSettings.State state = DepLensSettings.getInstance().getState();
        securityEnabled.setSelected(state.securityEnabled);
        cacheHours.setValue(state.cacheHours);
        ossIndexUser.setText(state.ossIndexUser);
        ossIndexToken.setText("");
        tokenHint.setText(state.ossIndexTokenStored
                ? "Token gespeichert – leer lassen, um es zu behalten."
                : "Ohne Benutzer und Token wird nur OSV genutzt.");
    }
}
