package com.example.gymtime.util

import java.io.BufferedReader

/**
 * Streaming RFC-4180 CSV parser that handles quoted fields, escaped `""` quotes,
 * embedded `\n` / `\r\n` newlines, and optional UTF-8 BOMs without reading an
 * entire file into memory.
 *
 * The caller owns [reader] and is responsible for closing it.
 */
internal object CsvParser {

    inline fun forEachRecord(
        reader: BufferedReader,
        onRecord: (recordNumber: Int, fields: List<String>) -> Unit
    ) {
        var recordNumber = 0
        var isStreamStart = true
        while (true) {
            val fields = readNextRecord(reader, isStreamStart) ?: break
            isStreamStart = false
            // Skip blank unquoted lines or whitespace-only single-column lines
            if (fields.size == 1 && fields[0].isBlank()) continue
            recordNumber++
            onRecord(recordNumber, fields)
        }
    }

    @PublishedApi
    internal fun readNextRecord(reader: BufferedReader, stripBom: Boolean): List<String>? {
        var ch = reader.read()
        if (stripBom && ch == '\uFEFF'.code) {
            ch = reader.read()
        }
        while (ch == '\r'.code || ch == '\n'.code) {
            ch = reader.read()
        }
        if (ch == -1) return null

        val fields = mutableListOf<String>()
        val fieldBuffer = StringBuilder()

        while (true) {
            fieldBuffer.setLength(0)
            if (ch == '"'.code) {
                while (true) {
                    ch = reader.read()
                    if (ch == -1) {
                        throw IllegalArgumentException("Unterminated quoted CSV field")
                    }
                    if (ch == '"'.code) {
                        val next = reader.read()
                        if (next == '"'.code) {
                            fieldBuffer.append('"')
                        } else {
                            ch = next
                            break
                        }
                    } else {
                        fieldBuffer.append(Char(ch))
                    }
                }
                while (ch != -1 && ch != ','.code && ch != '\n'.code && ch != '\r'.code) {
                    ch = reader.read()
                }
                fields.add(fieldBuffer.toString())
            } else {
                while (ch != -1 && ch != ','.code && ch != '\n'.code && ch != '\r'.code) {
                    fieldBuffer.append(Char(ch))
                    ch = reader.read()
                }
                fields.add(fieldBuffer.toString())
            }

            if (ch == ','.code) {
                ch = reader.read()
                continue
            }
            if (ch == '\r'.code) {
                reader.mark(1)
                val next = reader.read()
                if (next != '\n'.code && next != -1) {
                    reader.reset()
                }
            }
            break
        }
        return fields
    }
}
