/**
 * Stable contracts intended for mod integration.
 *
 * <p>{@code api.model}, {@code api.animation}, and {@code api.resource} contain
 * contracts usable by common, client, and server code. Client-only public API
 * belongs in {@code api.client}; server-only contracts, when needed, belong in
 * {@code api.server} and apply to both dedicated and integrated servers.</p>
 *
 * <p>Shared API must not depend on side-specific API. No API package may reference
 * the library's {@code core}, {@code client}, or {@code server} implementations.</p>
 */
package mods.mewkooo.bedrockmodel.api;
