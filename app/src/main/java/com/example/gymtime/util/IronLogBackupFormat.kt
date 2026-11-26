package com.example.gymtime.util

import java.util.Locale

/**
 * Shared IronLog Backup Archive (ZIP) Format v2 specification and zero-dependency
 * RFC-8259 JSON codec used for `manifest.json` and `preferences.json`.
 *
 * Designed for lossless bidirectional compatibility between Android IronLog and
 * companion clients (e.g., the IronLog PWA). All persisted weights remain in
 * canonical pounds (`lbs`).
 */
object IronLogBackupFormat {
    const val CURRENT_FORMAT_VERSION = 2
    const val MAX_SUPPORTED_FORMAT_VERSION = 2
    const val DB_SCHEMA_VERSION = 15
    const val CANONICAL_WEIGHT_UNIT = "lbs"
    const val SOURCE_ANDROID = "android"

    const val MANIFEST_ENTRY_NAME = "manifest.json"
    const val PREFERENCES_ENTRY_NAME = "preferences.json"

    val VALID_WEIGHT_UNITS = setOf("lbs", "kg")
    private const val MAX_LOADING_SIDES = 16
    private val HEX_COLOR_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    data class BackupTableCounts(
        val muscleGroups: Int = 0,
        val exercises: Int = 0,
        val routines: Int = 0,
        val routineDays: Int = 0,
        val routineExercises: Int = 0,
        val workouts: Int = 0,
        val sets: Int = 0,
        val workoutExerciseInstances: Int = 0
    )

    data class BackupManifest(
        val formatVersion: Int = CURRENT_FORMAT_VERSION,
        val dbSchemaVersion: Int = DB_SCHEMA_VERSION,
        val appVersion: String = "",
        val source: String = SOURCE_ANDROID,
        val exportedAtEpochMs: Long = 0L,
        val weightUnit: String = CANONICAL_WEIGHT_UNIT,
        val counts: BackupTableCounts = BackupTableCounts()
    )

    data class BackupPreferences(
        val userName: String = "Athlete",
        val themeColor: String = "lime",
        val customThemeColor: String? = null,
        val themeFont: String = "bebas_neue",
        val darkMode: Boolean = true,
        val timerAutoStart: Boolean = true,
        val timerAudioEnabled: Boolean = true,
        val timerVibrateEnabled: Boolean = true,
        val keepScreenOn: Boolean = false,
        val restDaysPerWeek: Int = 2,
        val bestStreak: Int = 0,
        val monthlyReportEnabled: Boolean = true,
        val barWeight: Float = 45f,
        val loadingSides: Int = 2,
        val availablePlates: List<Float> = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f),
        val usePlateInventory: Boolean = false,
        val plateInventory: Map<Float, Int> = emptyMap(),
        val preferredWeightUnit: String = CANONICAL_WEIGHT_UNIT
    )

    fun encodeManifest(manifest: BackupManifest): String {
        val countsObj = linkedMapOf<String, Any?>(
            "muscleGroups" to manifest.counts.muscleGroups,
            "exercises" to manifest.counts.exercises,
            "routines" to manifest.counts.routines,
            "routineDays" to manifest.counts.routineDays,
            "routineExercises" to manifest.counts.routineExercises,
            "workouts" to manifest.counts.workouts,
            "sets" to manifest.counts.sets,
            "workoutExerciseInstances" to manifest.counts.workoutExerciseInstances
        )
        val root = linkedMapOf<String, Any?>(
            "formatVersion" to manifest.formatVersion,
            "dbSchemaVersion" to manifest.dbSchemaVersion,
            "appVersion" to manifest.appVersion,
            "source" to manifest.source,
            "exportedAtEpochMs" to manifest.exportedAtEpochMs,
            "weightUnit" to manifest.weightUnit,
            "counts" to countsObj
        )
        return SimpleJson.stringify(root, pretty = true) + "\n"
    }

    fun decodeManifest(json: String): BackupManifest {
        val root = SimpleJson.parseObject(json)
            ?: throw InvalidBackupException("manifest.json must be a JSON object")

        val formatVersion = root.intValue("formatVersion")
            ?: throw InvalidBackupException("manifest.json is missing a valid formatVersion")
        if (formatVersion < 1) {
            throw InvalidBackupException("Invalid backup formatVersion: $formatVersion")
        }
        if (formatVersion > MAX_SUPPORTED_FORMAT_VERSION) {
            throw InvalidBackupException(
                "This backup uses format v$formatVersion, which is newer than this version of IronLog supports. Update IronLog before importing."
            )
        }

        val weightUnit = root.stringValue("weightUnit")
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotEmpty() }
            ?: CANONICAL_WEIGHT_UNIT
        if (weightUnit != CANONICAL_WEIGHT_UNIT) {
            throw InvalidBackupException("Unsupported backup storage weight unit: $weightUnit (expected $CANONICAL_WEIGHT_UNIT)")
        }

        val countsMap = root.objectValue("counts")
        val counts = if (countsMap != null) {
            BackupTableCounts(
                muscleGroups = (countsMap.intValue("muscleGroups") ?: 0).coerceAtLeast(0),
                exercises = (countsMap.intValue("exercises") ?: 0).coerceAtLeast(0),
                routines = (countsMap.intValue("routines") ?: 0).coerceAtLeast(0),
                routineDays = (countsMap.intValue("routineDays") ?: 0).coerceAtLeast(0),
                routineExercises = (countsMap.intValue("routineExercises") ?: 0).coerceAtLeast(0),
                workouts = (countsMap.intValue("workouts") ?: 0).coerceAtLeast(0),
                sets = (countsMap.intValue("sets") ?: 0).coerceAtLeast(0),
                workoutExerciseInstances = (countsMap.intValue("workoutExerciseInstances") ?: 0).coerceAtLeast(0)
            )
        } else {
            BackupTableCounts()
        }

        return BackupManifest(
            formatVersion = formatVersion,
            dbSchemaVersion = (root.intValue("dbSchemaVersion") ?: DB_SCHEMA_VERSION).coerceAtLeast(1),
            appVersion = root.stringValue("appVersion") ?: "",
            source = root.stringValue("source")?.takeIf { it.isNotBlank() } ?: "unknown",
            exportedAtEpochMs = root.longValue("exportedAtEpochMs") ?: 0L,
            weightUnit = weightUnit,
            counts = counts
        )
    }

    fun encodePreferences(prefs: BackupPreferences): String {
        val sanitized = sanitizePreferences(prefs)
        val inventoryObj = linkedMapOf<String, Any?>()
        sanitized.plateInventory.entries
            .sortedByDescending { it.key }
            .forEach { (weight, count) ->
                inventoryObj[PlateCalculator.formatWeight(weight)] = count
            }

        val root = linkedMapOf<String, Any?>(
            "userName" to sanitized.userName,
            "themeColor" to sanitized.themeColor,
            "customThemeColor" to sanitized.customThemeColor,
            "themeFont" to sanitized.themeFont,
            "darkMode" to sanitized.darkMode,
            "timerAutoStart" to sanitized.timerAutoStart,
            "timerAudioEnabled" to sanitized.timerAudioEnabled,
            "timerVibrateEnabled" to sanitized.timerVibrateEnabled,
            "keepScreenOn" to sanitized.keepScreenOn,
            "restDaysPerWeek" to sanitized.restDaysPerWeek,
            "bestStreak" to sanitized.bestStreak,
            "monthlyReportEnabled" to sanitized.monthlyReportEnabled,
            "barWeight" to sanitized.barWeight.toDouble(),
            "loadingSides" to sanitized.loadingSides,
            "availablePlates" to sanitized.availablePlates.map { it.toDouble() },
            "usePlateInventory" to sanitized.usePlateInventory,
            "plateInventory" to inventoryObj,
            "preferredWeightUnit" to sanitized.preferredWeightUnit
        )
        return SimpleJson.stringify(root, pretty = true) + "\n"
    }

    fun decodePreferences(json: String): BackupPreferences {
        val root = SimpleJson.parseObject(json)
            ?: throw InvalidBackupException("preferences.json must be a JSON object")

        val defaults = BackupPreferences()

        val plates = root.arrayValue("availablePlates")
            ?.mapNotNull { value ->
                (value as? Number)?.toFloat()
                    ?.takeIf { it.isFinite() }
                    ?.let(PlateCalculator::normalizePlateWeight)
            }
            ?.distinct()
            ?.sortedDescending()
            ?: defaults.availablePlates

        val inventoryRaw = mutableMapOf<Float, Int>()
        root.objectValue("plateInventory")?.forEach { (key, value) ->
            val weight = key.trim().toFloatOrNull()
                ?.let(PlateCalculator::normalizePlateWeight)
                ?: return@forEach
            val count = (value as? Number)?.toLong()
                ?.coerceIn(0L, PlateCalculator.MAX_PLATE_COUNT.toLong())
                ?.toInt()
                ?: return@forEach
            inventoryRaw[weight] = count
        }

        val raw = BackupPreferences(
            userName = root.stringValue("userName")?.trim()?.takeIf { it.isNotEmpty() } ?: defaults.userName,
            themeColor = root.stringValue("themeColor")?.trim()?.takeIf { it.isNotEmpty() } ?: defaults.themeColor,
            customThemeColor = root.stringValue("customThemeColor")?.trim()?.takeIf { HEX_COLOR_REGEX.matches(it) },
            themeFont = root.stringValue("themeFont")?.trim()?.takeIf { it.isNotEmpty() } ?: defaults.themeFont,
            darkMode = root.booleanValue("darkMode") ?: defaults.darkMode,
            timerAutoStart = root.booleanValue("timerAutoStart") ?: defaults.timerAutoStart,
            timerAudioEnabled = root.booleanValue("timerAudioEnabled") ?: defaults.timerAudioEnabled,
            timerVibrateEnabled = root.booleanValue("timerVibrateEnabled") ?: defaults.timerVibrateEnabled,
            keepScreenOn = root.booleanValue("keepScreenOn") ?: defaults.keepScreenOn,
            restDaysPerWeek = root.intValue("restDaysPerWeek") ?: defaults.restDaysPerWeek,
            bestStreak = root.intValue("bestStreak") ?: defaults.bestStreak,
            monthlyReportEnabled = root.booleanValue("monthlyReportEnabled") ?: defaults.monthlyReportEnabled,
            barWeight = root.floatValue("barWeight") ?: defaults.barWeight,
            loadingSides = root.intValue("loadingSides") ?: defaults.loadingSides,
            availablePlates = plates,
            usePlateInventory = root.booleanValue("usePlateInventory") ?: defaults.usePlateInventory,
            plateInventory = inventoryRaw,
            preferredWeightUnit = root.stringValue("preferredWeightUnit")
                ?.trim()
                ?.lowercase(Locale.US)
                ?.takeIf { it in VALID_WEIGHT_UNITS }
                ?: defaults.preferredWeightUnit
        )
        return sanitizePreferences(raw)
    }

    fun sanitizePreferences(prefs: BackupPreferences): BackupPreferences {
        val validCustomColor = prefs.customThemeColor?.trim()?.takeIf { HEX_COLOR_REGEX.matches(it) }
        val normalizedThemeColor = if (prefs.themeColor == "custom" && validCustomColor == null) {
            "lime"
        } else {
            prefs.themeColor.trim().ifEmpty { "lime" }
        }
        // Custom font SAF URIs are device-local and non-portable across devices.
        val normalizedFont = if (prefs.themeFont.equals("custom", ignoreCase = true)) {
            "bebas_neue"
        } else {
            prefs.themeFont.trim().ifEmpty { "bebas_neue" }
        }
        val safeBarWeight = if (prefs.barWeight.isFinite()) {
            prefs.barWeight.coerceIn(0f, PlateCalculator.MAX_SUPPORTED_WEIGHT)
        } else {
            45f
        }
        val safePlates = prefs.availablePlates
            .mapNotNull { it.takeIf(Float::isFinite)?.let(PlateCalculator::normalizePlateWeight) }
            .distinct()
            .sortedDescending()

        return BackupPreferences(
            userName = prefs.userName.trim().ifEmpty { "Athlete" },
            themeColor = normalizedThemeColor,
            customThemeColor = validCustomColor,
            themeFont = normalizedFont,
            darkMode = prefs.darkMode,
            timerAutoStart = prefs.timerAutoStart,
            timerAudioEnabled = prefs.timerAudioEnabled,
            timerVibrateEnabled = prefs.timerVibrateEnabled,
            keepScreenOn = prefs.keepScreenOn,
            restDaysPerWeek = prefs.restDaysPerWeek.coerceIn(0, 7),
            bestStreak = prefs.bestStreak.coerceAtLeast(0),
            monthlyReportEnabled = prefs.monthlyReportEnabled,
            barWeight = safeBarWeight,
            loadingSides = prefs.loadingSides.coerceIn(1, MAX_LOADING_SIDES),
            availablePlates = safePlates,
            usePlateInventory = prefs.usePlateInventory,
            plateInventory = PlateCalculator.sanitizePlateInventory(prefs.plateInventory),
            preferredWeightUnit = prefs.preferredWeightUnit.lowercase(Locale.US)
                .takeIf { it in VALID_WEIGHT_UNITS }
                ?: CANONICAL_WEIGHT_UNIT
        )
    }

    private fun Map<String, Any?>.stringValue(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.booleanValue(key: String): Boolean? = this[key] as? Boolean
    private fun Map<String, Any?>.intValue(key: String): Int? = when (val v = this[key]) {
        is Int -> v
        is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null
        is Double -> if (v.isFinite() && v % 1.0 == 0.0 && v >= Int.MIN_VALUE && v <= Int.MAX_VALUE) v.toInt() else null
        else -> null
    }
    private fun Map<String, Any?>.longValue(key: String): Long? = when (val v = this[key]) {
        is Long -> v
        is Int -> v.toLong()
        is Double -> if (v.isFinite() && v % 1.0 == 0.0) v.toLong() else null
        else -> null
    }
    private fun Map<String, Any?>.floatValue(key: String): Float? = when (val v = this[key]) {
        is Number -> v.toFloat().takeIf { it.isFinite() }
        else -> null
    }
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.arrayValue(key: String): List<Any?>? = this[key] as? List<Any?>
}

class InvalidBackupException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * Minimal, strict RFC-8259 JSON parser and serializer that avoids Android's stubbed
 * `org.json` classes under JVM unit tests while adding zero runtime dependencies.
 */
internal object SimpleJson {
    private const val MAX_DEPTH = 32

    fun stringify(value: Any?, pretty: Boolean = false): String {
        val sb = StringBuilder()
        writeValue(sb, value, pretty, indent = 0)
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(input: String): Map<String, Any?>? {
        val stripped = if (input.startsWith("\uFEFF")) input.substring(1) else input
        val parser = Parser(stripped)
        val result = parser.parseValue(depth = 0)
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            throw InvalidBackupException("Unexpected trailing content in JSON at offset ${parser.pos}")
        }
        return result as? Map<String, Any?>
    }

    private fun writeValue(sb: StringBuilder, value: Any?, pretty: Boolean, indent: Int) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long -> sb.append(value.toString())
            is Float -> {
                require(value.isFinite()) { "Non-finite float cannot be serialized to JSON" }
                sb.append(formatJsonDouble(value.toDouble()))
            }
            is Double -> {
                require(value.isFinite()) { "Non-finite double cannot be serialized to JSON" }
                sb.append(formatJsonDouble(value))
            }
            is String -> writeEscapedString(sb, value)
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append('{')
                val nextIndent = indent + 2
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    if (pretty) {
                        sb.append('\n')
                        repeat(nextIndent) { sb.append(' ') }
                    }
                    writeEscapedString(sb, k.toString())
                    sb.append(if (pretty) ": " else ":")
                    writeValue(sb, v, pretty, nextIndent)
                }
                if (pretty) {
                    sb.append('\n')
                    repeat(indent) { sb.append(' ') }
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                if (items.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append('[')
                items.forEachIndexed { index, item ->
                    if (index > 0) sb.append(if (pretty) ", " else ",")
                    writeValue(sb, item, pretty, indent + 2)
                }
                sb.append(']')
            }
            else -> writeEscapedString(sb, value.toString())
        }
    }

    private fun formatJsonDouble(value: Double): String {
        val longVal = value.toLong()
        return if (value == longVal.toDouble() && value >= -1e15 && value <= 1e15) {
            // Keep clean decimal for standard floating-point fields when needed, or exact representation
            if (value % 1.0 == 0.0) "$longVal.0" else value.toString()
        } else {
            value.toString()
        }
    }

    private fun writeEscapedString(sb: StringBuilder, text: String) {
        sb.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }

    private class Parser(private val src: String) {
        var pos: Int = 0

        fun isAtEnd(): Boolean = pos >= src.length

        fun skipWhitespace() {
            while (pos < src.length && src[pos] in charArrayOf(' ', '\t', '\r', '\n')) {
                pos++
            }
        }

        fun parseValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw InvalidBackupException("JSON nesting too deep")
            skipWhitespace()
            if (isAtEnd()) throw InvalidBackupException("Unexpected end of JSON")
            return when (src[pos]) {
                '{' -> parseObjectInternal(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw InvalidBackupException("Unexpected character '${src[pos]}' in JSON at offset $pos")
            }
        }

        private fun parseObjectInternal(depth: Int): Map<String, Any?> {
            pos++ // '{'
            skipWhitespace()
            val map = linkedMapOf<String, Any?>()
            if (!isAtEnd() && src[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (isAtEnd() || src[pos] != '"') {
                    throw InvalidBackupException("Expected JSON string key at offset $pos")
                }
                val key = parseString()
                skipWhitespace()
                if (isAtEnd() || src[pos] != ':') {
                    throw InvalidBackupException("Expected ':' after JSON key '$key' at offset $pos")
                }
                pos++
                map[key] = parseValue(depth)
                skipWhitespace()
                if (isAtEnd()) throw InvalidBackupException("Unterminated JSON object")
                when (src[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw InvalidBackupException("Expected ',' or '}' in JSON object at offset $pos")
                }
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            pos++ // '['
            skipWhitespace()
            val list = mutableListOf<Any?>()
            if (!isAtEnd() && src[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue(depth))
                skipWhitespace()
                if (isAtEnd()) throw InvalidBackupException("Unterminated JSON array")
                when (src[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw InvalidBackupException("Expected ',' or ']' in JSON array at offset $pos")
                }
            }
        }

        private fun parseString(): String {
            pos++ // '"'
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw InvalidBackupException("Unterminated escape in JSON string")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) {
                                    throw InvalidBackupException("Invalid unicode escape in JSON")
                                }
                                val hex = src.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw InvalidBackupException("Invalid unicode escape \\u$hex")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw InvalidBackupException("Invalid escape '\\$esc' in JSON string")
                        }
                    }
                    else -> {
                        if (ch.code < 0x20) {
                            throw InvalidBackupException("Unescaped control character in JSON string")
                        }
                        sb.append(ch)
                    }
                }
            }
            throw InvalidBackupException("Unterminated JSON string")
        }

        private fun parseLiteral(expected: String, value: Any?): Any? {
            if (src.startsWith(expected, pos)) {
                pos += expected.length
                return value
            }
            throw InvalidBackupException("Invalid JSON token at offset $pos")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            if (pos >= src.length || src[pos] !in '0'..'9') {
                throw InvalidBackupException("Invalid JSON number at offset $start")
            }
            if (src[pos] == '0') {
                pos++
            } else {
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            var isFloating = false
            if (pos < src.length && src[pos] == '.') {
                isFloating = true
                pos++
                if (pos >= src.length || src[pos] !in '0'..'9') {
                    throw InvalidBackupException("Invalid fractional JSON number at offset $start")
                }
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloating = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                if (pos >= src.length || src[pos] !in '0'..'9') {
                    throw InvalidBackupException("Invalid exponent in JSON number at offset $start")
                }
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            val token = src.substring(start, pos)
            return if (isFloating) {
                token.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?: throw InvalidBackupException("Invalid floating-point number '$token'")
            } else {
                token.toLongOrNull()
                    ?: token.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?: throw InvalidBackupException("Invalid integer '$token'")
            }
        }
    }
}
