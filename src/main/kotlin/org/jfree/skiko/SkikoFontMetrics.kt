/* ===============
 * SkikoGraphics2D
 * ===============
 *
 * (C)opyright 2023-present, by David Gilbert.
 *
 * The SkikoGraphics2D class has been developed by David Gilbert for
 * use with Orson Charts (https://github.com/jfree/orson-charts) and
 * JFreeChart (https://www.jfree.org/jfreechart).  It may be useful for other
 * code that uses the Graphics2D API provided by Java2D.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *   - Neither the name of the Object Refinery Limited nor the
 *     names of its contributors may be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL OBJECT REFINERY LIMITED BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.jfree.skiko

import org.jetbrains.skia.FontMetrics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Font

/**
 * Returns font metrics.
 */
class SkikoFontMetrics(private val skikoFont: org.jetbrains.skia.Font, awtFont: Font) : java.awt.FontMetrics(awtFont) {

    /** Skiko font metrics.  */
    @Transient
    private val metrics: FontMetrics = skikoFont.metrics

    /**
     * Returns the leading.
     *
     * @return The leading.
     */
    override fun getLeading(): Int {
        val result = metrics.leading.toInt()
        LOGGER.debug("getLeading() -> {}", result)
        return result
    }

    /**
     * Returns the ascent for the font.
     *
     * @return The ascent.
     */
    override fun getAscent(): Int {
        val result = -metrics.ascent.toInt()
        LOGGER.debug("getAscent() -> {}", result)
        return result
    }

    /**
     * Returns the descent for the font.
     *
     * @return The descent.
     */
    override fun getDescent(): Int {
        val result = metrics.descent.toInt()
        LOGGER.debug("getDescent() -> {}", result)
        return result
    }

    /**
     * Returns the width of the specified character.
     *
     * @param ch  the character.
     *
     * @return The width.
     */
    override fun charWidth(ch: Char): Int {
        val result = skikoFont.measureTextWidth(ch.toString()).toInt()
        LOGGER.debug("charWidth({}) -> {}", ch, result)
        return result
    }

    /**
     * Returns the width of a character sequence.
     *
     * @param data  the characters.
     * @param off  the offset.
     * @param len  the length.
     *
     * @return The width of the character sequence.
     */
    override fun charsWidth(data: CharArray, off: Int, len: Int): Int {
        val result = skikoFont.measureTextWidth(data.concatToString(off, off + len)).toInt()
        LOGGER.debug("charsWidth({}, {}, {}) -> {}", data, off, len, result)
        return result
    }

    companion object {
        private const val serialVersionUID = 1L
        private val LOGGER: Logger = LoggerFactory.getLogger(SkikoFontMetrics::class.java)
    }
}