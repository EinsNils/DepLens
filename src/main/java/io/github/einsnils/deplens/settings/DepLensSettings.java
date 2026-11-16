package io.github.einsnils.deplens.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import java.time.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Einstellungen von DepLens; das OSS-Index-Token liegt im IntelliJ-Passwortspeicher. */
@Service(Service.Level.APP)
@State(name = "DepLensSettings", storages = @Storage("deplens.xml"))
public final class DepLensSettings implements PersistentStateComponent<DepLensSettings.State> {

    public interface Listener {
        void settingsChanged();
    }

    public static final Topic<Listener> TOPIC = Topic.create("DepLens settings", Listener.class);

    public static final class State {
        public boolean securityEnabled = true;
        public int cacheHours = 24;
        public String ossIndexUser = "";
        public boolean ossIndexTokenStored;
    }

    private State state = new State();

    public static DepLensSettings getInstance() {
        return ApplicationManager.getApplication().getService(DepLensSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        state = loaded;
    }

    public boolean isSecurityEnabled() {
        return state.securityEnabled;
    }

    public Duration cacheDuration() {
        return Duration.ofHours(Math.max(1, state.cacheHours));
    }

    public String ossIndexUser() {
        return state.ossIndexUser == null ? "" : state.ossIndexUser.strip();
    }

    /** Blockiert (Passwortspeicher) – nicht auf dem EDT aufrufen. */
    public @Nullable String ossIndexToken() {
        return state.ossIndexTokenStored ? PasswordSafe.getInstance().getPassword(credentials()) : null;
    }

    /** Blockiert (Passwortspeicher) – nicht auf dem EDT aufrufen. {@code null} löscht das Token. */
    public void storeOssIndexToken(@Nullable String token) {
        PasswordSafe.getInstance().setPassword(credentials(), token);
    }

    public void notifyChanged() {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).settingsChanged();
    }

    private static CredentialAttributes credentials() {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("DepLens", "OSS Index"));
    }
}
