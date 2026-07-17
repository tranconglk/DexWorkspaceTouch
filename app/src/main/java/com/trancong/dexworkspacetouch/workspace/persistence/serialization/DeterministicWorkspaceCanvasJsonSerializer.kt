package com.trancong.dexworkspacetouch.workspace.persistence.serialization

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasValidator
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.persistence.domain.WorkspacePersistenceException

class DeterministicWorkspaceCanvasJsonSerializer(
    private val validator: WorkspaceCanvasValidator = WorkspaceCanvasValidator(),
) : WorkspaceCanvasSerializer {
    override fun encode(canvas: WorkspaceCanvas): String {
        validate(canvas)
        return buildString {
            append("{\"cells\":[")
            canvas.cells.forEachIndexed { index, cell ->
                if (index > 0) append(',')
                append("{\"id\":").appendQuoted(cell.id)
                append(",\"bounds\":{\"left\":").append(cell.bounds.left.canonical())
                append(",\"top\":").append(cell.bounds.top.canonical())
                append(",\"right\":").append(cell.bounds.right.canonical())
                append(",\"bottom\":").append(cell.bounds.bottom.canonical()).append('}')
                append(",\"app\":")
                val app = cell.app
                if (app == null) {
                    append("null")
                } else {
                    append("{\"packageName\":").appendQuoted(app.packageName)
                    append(",\"activityName\":")
                    if (app.activityName == null) append("null") else appendQuoted(app.activityName)
                    append(",\"label\":").appendQuoted(app.label).append('}')
                }
                append('}')
            }
            append("]}")
        }
    }

    override fun decode(json: String, schemaVersion: Int): WorkspaceCanvas {
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw WorkspacePersistenceException.UnsupportedSchema(schemaVersion)
        }
        return try {
            val root = JsonParser(json).parse().objectValue("root")
            val cells = root.required("cells").arrayValue("cells").mapIndexed { index, raw ->
                val value = raw.objectValue("cells[$index]")
                val bounds = value.required("bounds").objectValue("bounds")
                WorkspaceCell(
                    id = value.required("id").stringValue("id"),
                    bounds = NormalizedBounds(
                        left = bounds.required("left").floatValue("left"),
                        top = bounds.required("top").floatValue("top"),
                        right = bounds.required("right").floatValue("right"),
                        bottom = bounds.required("bottom").floatValue("bottom"),
                    ),
                    app = value.requiredNullable("app")?.let { rawApp ->
                        val app = rawApp.objectValue("app")
                        AssignedApp(
                            packageName = app.required("packageName").stringValue("packageName"),
                            activityName = app.requiredNullable("activityName")?.stringValue("activityName"),
                            label = app.required("label").stringValue("label"),
                        )
                    },
                )
            }
            WorkspaceCanvas(cells).also(::validate)
        } catch (error: WorkspacePersistenceException) {
            throw error
        } catch (error: Exception) {
            throw WorkspacePersistenceException.SerializationFailure("Invalid workspace canvas JSON", error)
        }
    }

    private fun validate(canvas: WorkspaceCanvas) {
        val issues = validator.validate(canvas)
        if (issues.isNotEmpty()) {
            throw WorkspacePersistenceException.SerializationFailure(
                "Invalid workspace canvas: ${issues.joinToString()}",
            )
        }
    }

    private fun Float.canonical(): String {
        if (!isFinite()) throw WorkspacePersistenceException.SerializationFailure("Bounds must be finite")
        return toString()
    }

    private fun StringBuilder.appendQuoted(value: String): StringBuilder {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else append(character)
            }
        }
        return append('"')
    }

    private companion object { const val SUPPORTED_SCHEMA_VERSION = 1 }
}

private fun Map<String, Any?>.required(name: String): Any =
    if (!containsKey(name) || get(name) == null) malformed("Missing '$name'") else getValue(name)!!

private fun Map<String, Any?>.requiredNullable(name: String): Any? =
    if (!containsKey(name)) malformed("Missing '$name'") else get(name)

private fun Any?.objectValue(name: String): Map<String, Any?> =
    (this as? Map<*, *>)?.entries?.associate { (key, value) ->
        (key as? String ?: malformed("Invalid key in '$name'")) to value
    } ?: malformed("'$name' must be an object")

private fun Any?.arrayValue(name: String): List<Any?> =
    this as? List<Any?> ?: malformed("'$name' must be an array")

private fun Any?.stringValue(name: String): String =
    this as? String ?: malformed("'$name' must be a string")

private fun Any?.floatValue(name: String): Float {
    val value = (this as? Double)?.toFloat() ?: malformed("'$name' must be a number")
    if (!value.isFinite()) malformed("'$name' must be finite")
    return value
}

private fun malformed(message: String): Nothing =
    throw WorkspacePersistenceException.SerializationFailure(message)

private class JsonParser(private val source: String) {
    private var position = 0

    fun parse(): Any? {
        skipWhitespace()
        val value = readValue()
        skipWhitespace()
        if (position != source.length) fail("Unexpected trailing input")
        return value
    }

    private fun readValue(): Any? = when (peek()) {
        '{' -> readObject()
        '[' -> readArray()
        '"' -> readString()
        'n' -> { expect("null"); null }
        't' -> { expect("true"); true }
        'f' -> { expect("false"); false }
        '-', in '0'..'9' -> readNumber()
        else -> fail("Expected JSON value")
    }

    private fun readObject(): Map<String, Any?> {
        take('{')
        skipWhitespace()
        val result = linkedMapOf<String, Any?>()
        if (consume('}')) return result
        while (true) {
            skipWhitespace()
            val key = readString()
            if (result.containsKey(key)) fail("Duplicate object key '$key'")
            skipWhitespace(); take(':'); skipWhitespace()
            result[key] = readValue()
            skipWhitespace()
            if (consume('}')) return result
            take(','); skipWhitespace()
        }
    }

    private fun readArray(): List<Any?> {
        take('['); skipWhitespace()
        val result = mutableListOf<Any?>()
        if (consume(']')) return result
        while (true) {
            result += readValue(); skipWhitespace()
            if (consume(']')) return result
            take(','); skipWhitespace()
        }
    }

    private fun readString(): String {
        take('"')
        return buildString {
            while (true) {
                if (position >= source.length) fail("Unterminated string")
                when (val character = source[position++]) {
                    '"' -> return@buildString
                    '\\' -> append(readEscape())
                    else -> {
                        if (character.code < 0x20) fail("Control character in string")
                        append(character)
                    }
                }
            }
        }
    }

    private fun readEscape(): Char = when (val escaped = source.getOrNull(position++) ?: fail("Bad escape")) {
        '"', '\\', '/' -> escaped
        'b' -> '\b'; 'f' -> '\u000C'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
        'u' -> {
            val end = position + 4
            if (end > source.length) fail("Bad Unicode escape")
            source.substring(position, end).toIntOrNull(16)?.toChar()
                ?.also { position = end } ?: fail("Bad Unicode escape")
        }
        else -> fail("Unknown escape")
    }

    private fun readNumber(): Double {
        val start = position
        if (peek() == '-') position++
        if (peek() == '0') position++ else readDigits()
        if (peekOrNull() == '.') { position++; readDigits() }
        if (peekOrNull() == 'e' || peekOrNull() == 'E') {
            position++
            if (peekOrNull() == '+' || peekOrNull() == '-') position++
            readDigits()
        }
        return source.substring(start, position).toDoubleOrNull() ?: fail("Invalid number")
    }

    private fun readDigits() {
        val start = position
        while (peekOrNull()?.isDigit() == true) position++
        if (start == position) fail("Expected digit")
    }

    private fun expect(value: String) {
        if (!source.startsWith(value, position)) fail("Expected '$value'")
        position += value.length
    }

    private fun take(expected: Char) {
        if (peek() != expected) fail("Expected '$expected'")
        position++
    }

    private fun consume(expected: Char): Boolean = if (peekOrNull() == expected) {
        position++; true
    } else false

    private fun skipWhitespace() { while (peekOrNull()?.isWhitespace() == true) position++ }
    private fun peek(): Char = peekOrNull() ?: fail("Unexpected end of input")
    private fun peekOrNull(): Char? = source.getOrNull(position)
    private fun fail(message: String): Nothing = malformed("$message at position $position")
}
