/**
 * The event plugin pipeline. Every event runs through the plugins in ascending priority order
 * before it is queued; a plugin can enrich the event or cancel it.
 */
@NullMarked
package com.exceptionless.plugins;

import org.jspecify.annotations.NullMarked;
