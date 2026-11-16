package dev.nz3x.deplens.security;

import org.jetbrains.annotations.Nullable;

/** Eine Lücke, die die geprüfte Version betrifft, mit der Version, die sie behebt (falls bekannt). */
public record Finding(Vulnerability vulnerability, @Nullable MavenVersion fixedIn) {
}
