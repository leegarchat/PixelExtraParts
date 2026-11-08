package io.github.libxposed.api;

/**
 * Exception policy for a hook (API 102 subset).
 * Currently only {@link #PASSTHROUGH} is observed in the wild;
 * the host honors passthrough semantics (hook exceptions reach
 * the caller) and accepts other modes as no-ops with a log.
 */
public enum ExceptionMode {
    PASSTHROUGH
}
