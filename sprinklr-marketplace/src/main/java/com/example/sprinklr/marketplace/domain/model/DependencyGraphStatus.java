package com.example.sprinklr.marketplace.domain.model;

/**
 * Lifecycle of a per-connection tool dependency graph.
 *
 * <ul>
 *   <li>{@code PENDING}  — connection saved, graph generation not yet finished.</li>
 *   <li>{@code READY}    — graph generated and validated; safe to drive tool expansion.</li>
 *   <li>{@code FAILED}   — generation or validation failed; chat falls back to router-only
 *                          selection (no dependency expansion) for this server.</li>
 * </ul>
 */
public enum DependencyGraphStatus {
    PENDING,
    READY,
    FAILED
}
