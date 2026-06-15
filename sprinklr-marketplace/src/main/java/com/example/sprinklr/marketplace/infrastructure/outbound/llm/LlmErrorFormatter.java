package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import io.netty.channel.ConnectTimeoutException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Maps LLM infrastructure failures to short messages suitable for the chat UI.
 * The Sprinklr LLM router is internal-only and requires VPN for connectivity.
 */
public final class LlmErrorFormatter {

    /** Shown when the router cannot be reached — almost always means VPN is not connected. */
    public static final String VPN_REQUIRED_MESSAGE =
            "I couldn't connect to the Sprinklr internal LLM router. "
                    + "This service is only available when you're connected to the Sprinklr VPN. "
                    + "Please connect to VPN and try again.";

    /** Shown when a pooled connection went stale or the network dropped mid-request. */
    public static final String TRANSIENT_NETWORK_MESSAGE =
            "The connection to the LLM router was interrupted. Please try again.";

    private LlmErrorFormatter() {
    }

    public static String toUserMessage(Throwable error) {
        if (error == null) {
            return genericFailureMessage();
        }

        if (isTransientNetworkFailure(error)) {
            return TRANSIENT_NETWORK_MESSAGE;
        }

        if (isVpnOrNetworkFailure(error)) {
            return VPN_REQUIRED_MESSAGE;
        }

        String message = error.getMessage() != null ? error.getMessage() : "";

        if (error instanceof LlmClientException) {
            if (message.contains("HTTP 401") || message.contains("HTTP 403")) {
                return "The LLM router rejected the request (authentication failed). "
                        + "Please refresh your LLM_ROUTER_COOKIE and restart the server.";
            }
            if (message.contains("Failed to parse")) {
                return "The LLM router returned a response we could not parse. Please try again.";
            }
            return "The LLM request failed: " + shorten(message, 200);
        }

        return genericFailureMessage();
    }

    /**
     * Short-lived network glitches (stale pooled connections, ALB idle timeout) — safe to retry once.
     */
    public static boolean isTransientNetworkFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketException) {
                String msg = current.getMessage();
                if (msg != null && isTransientNetworkMessage(msg)) {
                    return true;
                }
            }
            String msg = current.getMessage();
            if (msg != null && isTransientNetworkMessage(msg)) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

    /**
     * Detects failures that indicate the internal router is unreachable (VPN down, firewall, wrong network).
     */
    public static boolean isVpnOrNetworkFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectTimeoutException
                    || current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof NoRouteToHostException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null && isUnreachableNetworkMessage(msg)) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

    private static boolean isTransientNetworkMessage(String message) {
        return message.contains("Connection reset")
                || message.contains("Broken pipe")
                || message.contains("Connection closed")
                || message.contains("closed by peer");
    }

    private static boolean isUnreachableNetworkMessage(String message) {
        return message.contains("connection timed out")
                || message.contains("ConnectTimeoutException")
                || message.contains("Connection refused")
                || message.contains("UnknownHostException")
                || message.contains("No route to host")
                || message.contains("Failed to connect")
                || message.contains("Network is unreachable");
    }

    private static String genericFailureMessage() {
        return "Something went wrong while contacting the LLM. Please try again.";
    }

    private static String shorten(String value, int maxLen) {
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen - 3) + "...";
    }
}
