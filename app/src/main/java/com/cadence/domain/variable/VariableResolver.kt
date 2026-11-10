package com.cadence.domain.variable

import com.cadence.domain.engine.ExecutionContext
import java.time.Instant

/**
 * Resolves variable names and fills `{name}` placeholders in text.
 *
 * Lookup order:
 * 1. local variables of the run (including `http.*` written by HTTP requests);
 * 2. `macro.*` and `trigger.*` from the run itself;
 * 3. other dotted names: built-in device values from [system] (cached once per run);
 * 4. plain names: global variables.
 *
 * Unknown placeholders are left as written, so a typo is visible in the result.
 */
class VariableResolver(
    private val globals: GlobalVariableRepository?,
    private val system: SystemVariableSource?,
    /** When a macro last ran (excluding runs skipped by constraints), for `{macro.last_run}`. */
    private val lastRunOf: (suspend (macroId: Long) -> Instant?)? = null,
) {
    /** The value of [name], or `null` if no variable by that name exists. */
    suspend fun lookup(name: String, context: ExecutionContext): String? {
        context.locals[name]?.let { return it }
        return when {
            name == "macro.name" -> context.macro.name
            name == "macro.id" -> context.macro.id.toString()
            name == "macro.category" -> context.macro.category
            name == "macro.last_run" -> lastRunOf?.invoke(context.macro.id)?.toString().orEmpty()
            name == "trigger.time" -> context.trigger.occurredAt.toString()
            name.startsWith("trigger.") -> context.trigger.data[name.removePrefix("trigger.")]
            '.' in name -> {
                val cacheKey = SYSTEM_CACHE_PREFIX + name
                if (context.runCache.containsKey(cacheKey)) {
                    context.runCache[cacheKey] as String?
                } else {
                    system?.value(name, context).also { context.runCache[cacheKey] = it }
                }
            }
            else -> globals?.get(name)
        }
    }

    /** Replaces every `{name}` in [template] with its value. */
    suspend fun render(template: String, context: ExecutionContext): String {
        if ('{' !in template) return template
        val result = StringBuilder(template.length)
        var last = 0
        for (match in Templates.PLACEHOLDER.findAll(template)) {
            result.append(template, last, match.range.first)
            val value = lookup(match.groupValues[1], context)
            result.append(value ?: match.value)
            last = match.range.last + 1
        }
        result.append(template, last, template.length)
        return result.toString()
    }

    /**
     * Drops cached built-in values starting with [keyPrefix] (e.g. `location.`), so the next
     * placeholder reads them again. Used after an action changes the underlying reading.
     */
    fun forgetCached(context: ExecutionContext, keyPrefix: String) {
        synchronized(context.runCache) {
            context.runCache.keys.removeAll { it.startsWith(SYSTEM_CACHE_PREFIX + keyPrefix) }
        }
    }

    companion object {
        private const val SYSTEM_CACHE_PREFIX = "system:"

        /** Resolves only run-scoped values. Used when no storage is wired up (tests). */
        val Empty = VariableResolver(globals = null, system = null)
    }
}

/** Placeholder parsing shared by the resolver and permission checks. */
object Templates {
    /**
     * `{name}` where name is a [VariableNames.isValidReference] name. Both braces are escaped:
     * the JVM accepts a bare `}`, but Android's ICU regex engine rejects it.
     */
    val PLACEHOLDER = Regex("\\{([A-Za-z_][A-Za-z0-9_.]{0,79})\\}")

    /** Names referenced in [text]. */
    fun placeholders(text: String): Set<String> =
        PLACEHOLDER.findAll(text).mapTo(mutableSetOf()) { it.groupValues[1] }
}
