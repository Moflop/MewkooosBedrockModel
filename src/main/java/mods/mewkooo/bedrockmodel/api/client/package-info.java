/**
 * Public API restricted to the physical client.
 *
 * <p>Use these types from client-isolated code and respect each API's thread and
 * lifecycle requirements. They must not be referenced by shared API, shared core,
 * or logical-server code, including the integrated server.</p>
 *
 * <p>Client API may depend on shared API, but must not reference the library's
 * implementation packages. Shared display metadata lives in
 * {@code api.model.display}; applying it to a render pose lives in
 * {@code api.client.display}.</p>
 */
package mods.mewkooo.bedrockmodel.api.client;
