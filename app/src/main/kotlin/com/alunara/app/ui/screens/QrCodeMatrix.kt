package com.alunara.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alunara.app.ui.theme.AlunaraPalette
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure-Kotlin QR Code encoder (Byte mode, ECC Level L, Versions 3 and 4).
 * Designed for crisp rendering of short LAN URLs (up to 78 UTF-8 bytes) without external dependencies.
 */
object QrCodeMatrix {
    private data class VersionSpec(
        val version: Int,
        val size: Int,
        val dataCodewords: Int,
        val eccCodewords: Int,
        val alignmentCenter: Int,
    )

    private val VERSION_3 = VersionSpec(version = 3, size = 29, dataCodewords = 55, eccCodewords = 15, alignmentCenter = 22)
    private val VERSION_4 = VersionSpec(version = 4, size = 33, dataCodewords = 80, eccCodewords = 20, alignmentCenter = 26)

    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) {
                x = x xor 0x11D
            }
        }
        for (i in 255 until 512) {
            gfExp[i] = gfExp[i - 255]
        }
    }

    data class Matrix(
        val version: Int,
        val size: Int,
        private val modules: BooleanArray,
    ) {
        fun get(row: Int, col: Int): Boolean = modules[row * size + col]
    }

    fun encode(content: String): Matrix {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val spec =
            when {
                bytes.size <= 53 -> VERSION_3
                bytes.size <= 78 -> VERSION_4
                else -> throw IllegalArgumentException("QR payload exceeds 78-byte Version 4-L limit (${bytes.size} bytes)")
            }

        val dataCodewords = buildDataCodewords(bytes, spec.dataCodewords)
        val eccCodewords = computeReedSolomonEcc(dataCodewords, spec.eccCodewords)
        val allCodewords = ByteArray(dataCodewords.size + eccCodewords.size)
        dataCodewords.copyInto(allCodewords, 0)
        eccCodewords.copyInto(allCodewords, dataCodewords.size)

        val n = spec.size
        val isFunction = BooleanArray(n * n)
        val baseModules = BooleanArray(n * n)

        fun setFunction(row: Int, col: Int, dark: Boolean) {
            if (row !in 0 until n || col !in 0 until n) return
            val idx = row * n + col
            isFunction[idx] = true
            baseModules[idx] = dark
        }

        drawFinderPattern(3, 3, ::setFunction)
        drawFinderPattern(3, n - 4, ::setFunction)
        drawFinderPattern(n - 4, 3, ::setFunction)
        drawAlignmentPattern(spec.alignmentCenter, spec.alignmentCenter, ::setFunction)

        for (i in 8 until n - 8) {
            val dark = (i % 2) == 0
            setFunction(6, i, dark)
            setFunction(i, 6, dark)
        }

        // Reserve format info strips & dark module before placing data bits
        for (i in 0..8) {
            setFunction(8, i, false)
            setFunction(i, 8, false)
        }
        for (i in n - 8 until n) {
            setFunction(8, i, false)
            setFunction(i, 8, false)
        }
        setFunction(4 * spec.version + 9, 8, true)

        placeDataBits(allCodewords, n, isFunction, baseModules)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestModules = baseModules

        for (mask in 0 until 8) {
            val candidate = baseModules.copyOf()
            applyMask(candidate, isFunction, n, mask)
            drawFormatBits(candidate, n, mask)
            val penalty = computePenalty(candidate, n)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestModules = candidate
            }
        }
        // Keep bestMask documented for diagnostics if needed
        @Suppress("UNUSED_VARIABLE")
        val selectedMask = bestMask
        return Matrix(version = spec.version, size = n, modules = bestModules)
    }

    private fun drawFinderPattern(
        centerRow: Int,
        centerCol: Int,
        setFunction: (Int, Int, Boolean) -> Unit,
    ) {
        for (dr in -4..4) {
            for (dc in -4..4) {
                val dist = max(abs(dr), abs(dc))
                setFunction(centerRow + dr, centerCol + dc, dist != 2 && dist != 4)
            }
        }
    }

    private fun drawAlignmentPattern(
        centerRow: Int,
        centerCol: Int,
        setFunction: (Int, Int, Boolean) -> Unit,
    ) {
        for (dr in -2..2) {
            for (dc in -2..2) {
                val dist = max(abs(dr), abs(dc))
                setFunction(centerRow + dr, centerCol + dc, dist != 1)
            }
        }
    }

    private fun buildDataCodewords(bytes: ByteArray, capacity: Int): ByteArray {
        val bits = ArrayList<Boolean>(capacity * 8)

        fun appendBits(value: Int, length: Int) {
            for (shift in length - 1 downTo 0) {
                bits += ((value ushr shift) and 1) == 1
            }
        }

        // Byte mode indicator (0100) + 8-bit character count (Versions 1..9)
        appendBits(0b0100, 4)
        appendBits(bytes.size, 8)
        for (b in bytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        val maxBits = capacity * 8
        val terminator = minOf(4, maxBits - bits.size)
        repeat(terminator) { bits += false }
        while (bits.size % 8 != 0) {
            bits += false
        }

        val out = ByteArray(capacity)
        val dataBytesCount = bits.size / 8
        for (i in 0 until dataBytesCount) {
            var byteVal = 0
            for (b in 0 until 8) {
                if (bits[i * 8 + b]) {
                    byteVal = byteVal or (1 shl (7 - b))
                }
            }
            out[i] = byteVal.toByte()
        }

        var padIndex = 0
        for (i in dataBytesCount until capacity) {
            out[i] = if (padIndex % 2 == 0) 0xEC.toByte() else 0x11.toByte()
            padIndex++
        }
        return out
    }

    private fun computeReedSolomonEcc(data: ByteArray, eccLen: Int): ByteArray {
        val gen = IntArray(eccLen + 1)
        gen[0] = 1
        var genLen = 1
        for (i in 0 until eccLen) {
            val root = gfExp[i]
            val next = IntArray(eccLen + 1)
            for (j in 0 until genLen) {
                next[j] = next[j] xor gen[j]
                next[j + 1] = next[j + 1] xor gfMul(gen[j], root)
            }
            next.copyInto(gen)
            genLen++
        }

        val rem = IntArray(eccLen)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor rem[0]
            System.arraycopy(rem, 1, rem, 0, eccLen - 1)
            rem[eccLen - 1] = 0
            for (j in 0 until eccLen) {
                rem[j] = rem[j] xor gfMul(gen[j + 1], factor)
            }
        }
        return ByteArray(eccLen) { rem[it].toByte() }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[gfLog[a] + gfLog[b]]
    }

    private fun placeDataBits(
        codewords: ByteArray,
        n: Int,
        isFunction: BooleanArray,
        modules: BooleanArray,
    ) {
        val totalBits = codewords.size * 8
        var bitIndex = 0
        var right = n - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until n) {
                for (j in 0 until 2) {
                    val col = right - j
                    val upward = ((right + 1) and 2) == 0
                    val row = if (upward) n - 1 - vert else vert
                    val idx = row * n + col
                    if (!isFunction[idx] && bitIndex < totalBits) {
                        val byteVal = codewords[bitIndex ushr 3].toInt() and 0xFF
                        val shift = 7 - (bitIndex and 7)
                        modules[idx] = ((byteVal ushr shift) and 1) == 1
                        bitIndex++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(
        modules: BooleanArray,
        isFunction: BooleanArray,
        n: Int,
        mask: Int,
    ) {
        for (r in 0 until n) {
            for (c in 0 until n) {
                val idx = r * n + c
                if (!isFunction[idx] && isMasked(r, c, mask)) {
                    modules[idx] = !modules[idx]
                }
            }
        }
    }

    private fun isMasked(r: Int, c: Int, mask: Int): Boolean =
        when (mask) {
            0 -> (r + c) % 2 == 0
            1 -> r % 2 == 0
            2 -> c % 3 == 0
            3 -> (r + c) % 3 == 0
            4 -> ((r / 2) + (c / 3)) % 2 == 0
            5 -> ((r * c) % 2 + (r * c) % 3) == 0
            6 -> (((r * c) % 2 + (r * c) % 3) % 2) == 0
            7 -> (((r + c) % 2 + (r * c) % 3) % 2) == 0
            else -> false
        }

    internal fun formatBitsForEccL(mask: Int): Int {
        val data = (0b01 shl 3) or (mask and 0x7)
        var rem = data shl 10
        for (i in 4 downTo 0) {
            if ((rem and (1 shl (i + 10))) != 0) {
                rem = rem xor (0x537 shl i)
            }
        }
        return ((data shl 10) or rem) xor 0x5412
    }

    private fun drawFormatBits(modules: BooleanArray, n: Int, mask: Int) {
        val bits = formatBitsForEccL(mask)
        fun bit(i: Int): Boolean = ((bits ushr i) and 1) == 1

        for (i in 0..5) modules[i * n + 8] = bit(i)
        modules[7 * n + 8] = bit(6)
        modules[8 * n + 8] = bit(7)
        modules[8 * n + 7] = bit(8)
        for (i in 9..14) modules[8 * n + (14 - i)] = bit(i)

        for (i in 0..7) modules[8 * n + (n - 1 - i)] = bit(i)
        for (i in 8..14) modules[(n - 15 + i) * n + 8] = bit(i)
    }

    private fun computePenalty(modules: BooleanArray, n: Int): Int {
        var penalty = 0
        var darkCount = 0

        for (r in 0 until n) {
            var rowRunColor = modules[r * n]
            var rowRunLen = 0
            var colRunColor = modules[r]
            var colRunLen = 0
            for (c in 0 until n) {
                val rColor = modules[r * n + c]
                if (rColor) darkCount++
                if (rColor == rowRunColor) {
                    rowRunLen++
                } else {
                    if (rowRunLen >= 5) penalty += 3 + (rowRunLen - 5)
                    rowRunColor = rColor
                    rowRunLen = 1
                }

                val cColor = modules[c * n + r]
                if (cColor == colRunColor) {
                    colRunLen++
                } else {
                    if (colRunLen >= 5) penalty += 3 + (colRunLen - 5)
                    colRunColor = cColor
                    colRunLen = 1
                }
            }
            if (rowRunLen >= 5) penalty += 3 + (rowRunLen - 5)
            if (colRunLen >= 5) penalty += 3 + (colRunLen - 5)
        }

        for (r in 0 until n - 1) {
            for (c in 0 until n - 1) {
                val color = modules[r * n + c]
                if (
                    modules[r * n + c + 1] == color &&
                    modules[(r + 1) * n + c] == color &&
                    modules[(r + 1) * n + c + 1] == color
                ) {
                    penalty += 3
                }
            }
        }

        val total = n * n
        val percentDeviation = abs((darkCount * 100 / total) - 50) / 5
        return penalty + percentDeviation * 10
    }
}

@Composable
fun QrCodeCanvas(
    content: String,
    modifier: Modifier = Modifier,
) {
    val matrix =
        remember(content) {
            runCatching { QrCodeMatrix.encode(content) }.getOrNull()
        }

    Box(
        modifier =
            modifier
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(12.dp),
    ) {
        if (matrix != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellSize = minOf(size.width, size.height) / matrix.size.toFloat()
                val offsetX = (size.width - cellSize * matrix.size) / 2f
                val offsetY = (size.height - cellSize * matrix.size) / 2f
                val inkColor = AlunaraPalette.FocusedButtonContent
                for (row in 0 until matrix.size) {
                    for (col in 0 until matrix.size) {
                        if (matrix.get(row, col)) {
                            drawRect(
                                color = inkColor,
                                topLeft = Offset(offsetX + col * cellSize, offsetY + row * cellSize),
                                // Slight 0.5px overlap prevents subpixel seams on scaled TV densities
                                size = Size(cellSize + 0.5f, cellSize + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}
