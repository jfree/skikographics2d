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

import org.jetbrains.skia.*
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Image
import java.awt.Paint
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.font.TextLayout
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ImageObserver
import java.awt.image.RenderedImage
import java.awt.image.renderable.RenderableImage
import java.text.AttributedCharacterIterator
import java.util.*
import java.util.function.Function
import kotlin.math.max

/**
 * An implementation of the Graphics2D API that targets the Skiko graphics API
 * (https://github.com/JetBrains/skiko).
 */
class SkikoGraphics2D : Graphics2D {

    /* members */
    /** log enabled in constructor if logger is at debug level (low perf overhead)  */
    private val debugLogEnabled: Boolean

    /** Rendering hints.  */
    private val hints = RenderingHints(
        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT
    )

    /** Surface from Skiko  */
    private var surface: Surface? = null
    private var width = 0
    private var height = 0

    /** Canvas from Skiko  */
    private var canvas: org.jetbrains.skia.Canvas? = null

    /** Paint used for drawing on Skiko canvas.  */
    private var skikoPaint: org.jetbrains.skia.Paint = org.jetbrains.skia.Paint()
        .setARGB(DEFAULT_COLOR.alpha, DEFAULT_COLOR.red, DEFAULT_COLOR.green, DEFAULT_COLOR.blue)

    /** The Skiko save/restore count, used to restore the original clip in setClip().  */
    private var restoreCount = 0
    private var awtPaint: Paint? = null

    /** Stores the AWT Color object for get/setColor().  */
    private var color: Color? = DEFAULT_COLOR
    private var stroke: Stroke? = DEFAULT_STROKE // nullable only for Graphics2D compatibility
    private var awtFont: Font = DEFAULT_FONT
    private var typeface: Typeface? = null
    private var skikoFont: org.jetbrains.skia.Font = org.jetbrains.skia.Font()

    /** The background color, used in the `clearRect()` method.  */
    private var background: Color? = null
    private var transform = AffineTransform()
    private var composite: Composite = AlphaComposite.getInstance(
        AlphaComposite.SRC_OVER, 1.0f
    )

    /** The user clip (can be null).  */
    private var clip: Shape? = null

    /**
     * The font render context.
     */
    private val fontRenderContext = DEFAULT_FONT_RENDER_CONTEXT

    /**
     * An instance that is reused in drawLine() to avoid creating a lot of garbage.
     */
    private var line = Line2D.Double()

    /**
     * An instance that is reused to avoid creating a lot of garbage.
     */
    private var rect = Rectangle2D.Double()

    /**
     * An instance that is reused to avoid creating a lot of garbage.
     */
    private var roundRect = RoundRectangle2D.Double()

    /**
     * An instance that is reused to avoid creating a lot of garbage.
     */
    private var oval = Ellipse2D.Double()

    /**
     * An instance that is reused to avoid creating a lot of garbage.
     */
    private var arc = Arc2D.Double()

    /**
     * The device configuration (this is lazily instantiated in the
     * getDeviceConfiguration() method).
     */
    private var deviceConfiguration: GraphicsConfiguration? = null

    /** Used and reused in the path() method below.  */
    private val coords = DoubleArray(6)

    /** Used and reused in the drawString() method below.  */
    private val sbStr = StringBuilder(256)

    /**
     * Creates a new instance with the specified height and width.
     *
     * @param width  the width.
     * @param height  the height.
     */
    constructor(width: Int, height: Int) {
        debugLogEnabled = LOGGER.isDebugEnabled
        if (debugLogEnabled) {
            LOGGER.debug("SkikoGraphics2D({}, {})", width, height)
        }
        this.width = width
        this.height = height

        surface = Surface.makeRasterN32Premul(width, height).also {
            init(it.canvas)
        }
    }

    /**
     * Creates a new instance with the specified height and width using an existing
     * canvas.
     *
     * @param canvas  the canvas (`null` not permitted).
     */
    constructor(canvas: org.jetbrains.skia.Canvas) {
        debugLogEnabled = LOGGER.isDebugEnabled
        if (debugLogEnabled) {
            LOGGER.debug("SkikoGraphics2D(Canvas)")
        }
        init(canvas)
    }

    /**
     * Copy-constructor: creates a new instance with the given parent SkikoGraphics2D.
     *
     * @param parent SkikoGraphics2D instance to copy (`null` not permitted).
     */
    private constructor(parent: SkikoGraphics2D) {
        debugLogEnabled = LOGGER.isDebugEnabled
        if (debugLogEnabled) {
            LOGGER.debug("SkikoGraphics2D(parent)")
        }
        canvas = parent.canvas
        setRenderingHints(parent.renderingHintsInternally)
        if (getRenderingHint(SkikoHints.KEY_FONT_MAPPING_FUNCTION) == null) {
            setRenderingHint(SkikoHints.KEY_FONT_MAPPING_FUNCTION, Function { s: String ->
                FONT_MAPPING[s]
            })
        }
        clip = parent.clip
        setBackground(parent.getBackground())
        this.paint = parent.paint
        setComposite(parent.getComposite())
        setStroke(parent.getStroke())
        this.font = parent.font
        setTransform(parent.transformInternally)

        // save the original clip settings so they can be restored later in setClip()
        restoreCount = canvas!!.save()
        if (debugLogEnabled) {
            LOGGER.debug("restoreCount updated to {}", restoreCount)
        }
    }

    /**
     * Creates a new instance using an existing canvas.
     *
     * @param canvas  the canvas (`null` not permitted).
     */
    private fun init(canvas: org.jetbrains.skia.Canvas) {
        this.canvas = canvas
        if (getRenderingHint(SkikoHints.KEY_FONT_MAPPING_FUNCTION) == null) {
            setRenderingHint(SkikoHints.KEY_FONT_MAPPING_FUNCTION, Function { s: String ->
                FONT_MAPPING[s]
            })
        }

        // use constants for quick initialization:
        background = DEFAULT_COLOR
        skikoPaint = org.jetbrains.skia.Paint()
            .setARGB(DEFAULT_COLOR.alpha, DEFAULT_COLOR.red, DEFAULT_COLOR.green, DEFAULT_COLOR.blue)
        paint = DEFAULT_COLOR
        setStroke(DEFAULT_STROKE)
        // use TYPEFACE_MAP cache:
        font = DEFAULT_FONT

        // save the original clip settings so they can be restored later in setClip()
        restoreCount = this.canvas!!.save()
        if (debugLogEnabled) {
            LOGGER.debug("restoreCount updated to {}", restoreCount)
        }
    }

    /**
     * Returns the Skiko surface that was created by this instance, or `null`.
     *
     * @return The Skiko surface (possibly `null`).
     */
    fun getSurface(): Surface? {
        return surface
    }

    /**
     * Creates a Skiko path from the outline of a Java2D shape.
     *
     * @param shape  the shape (`null` not permitted).
     *
     * @return A path.
     */
    private fun path(shape: Shape): Path {
        val p = Path() // TODO: reuse Path instances or not (async safety) ?
        val iterator = shape.getPathIterator(null)
        while (!iterator.isDone) {
            when (val segType = iterator.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> {
                    if (debugLogEnabled) {
                        LOGGER.debug("SEG_MOVETO: ({},{})", coords[0], coords[1])
                    }
                    p.moveTo(coords[0].toFloat(), coords[1].toFloat())
                }

                PathIterator.SEG_LINETO -> {
                    if (debugLogEnabled) {
                        LOGGER.debug("SEG_LINETO: ({},{})", coords[0], coords[1])
                    }
                    p.lineTo(coords[0].toFloat(), coords[1].toFloat())
                }

                PathIterator.SEG_QUADTO -> {
                    if (debugLogEnabled) {
                        LOGGER.debug(
                            "SEG_QUADTO: ({},{} {},{})", coords[0], coords[1], coords[2], coords[3]
                        )
                    }
                    p.quadTo(coords[0].toFloat(), coords[1].toFloat(), coords[2].toFloat(), coords[3].toFloat())
                }

                PathIterator.SEG_CUBICTO -> {
                    if (debugLogEnabled) {
                        LOGGER.debug(
                            "SEG_CUBICTO: ({},{} {},{} {},{})",
                            coords[0],
                            coords[1],
                            coords[2],
                            coords[3],
                            coords[4],
                            coords[5]
                        )
                    }
                    p.cubicTo(
                        coords[0].toFloat(),
                        coords[1].toFloat(),
                        coords[2].toFloat(),
                        coords[3].toFloat(),
                        coords[4].toFloat(),
                        coords[5].toFloat()
                    )
                }

                PathIterator.SEG_CLOSE -> {
                    if (debugLogEnabled) {
                        LOGGER.debug("SEG_CLOSE: ")
                    }
                    p.closePath()
                }

                else -> throw RuntimeException("Unrecognised segment type $segType")
            }
            iterator.next()
        }
        return p
    }

    /**
     * Draws the specified shape with the current `paint` and
     * `stroke`.  There is direct handling for `Line2D` and
     * `Rectangle2D`.  All other shapes are mapped to a `GeneralPath`
     * and then drawn (effectively as `Path2D` objects).
     *
     * @param s  the shape (`null` not permitted).
     *
     * @see .fill
     */
    override fun draw(s: Shape) {
        if (debugLogEnabled) {
            LOGGER.debug("draw(Shape) : {}", s)
        }
        skikoPaint.mode = PaintMode.STROKE
        when (s) {
            is Line2D -> {
                canvas?.drawLine(s.x1.toFloat(), s.y1.toFloat(), s.x2.toFloat(), s.y2.toFloat(), skikoPaint)
            }

            is Rectangle2D -> {
                if (s.width < 0.0 || s.height < 0.0) {
                    return
                }
                canvas?.drawRect(
                    Rect.makeXYWH(s.x.toFloat(), s.y.toFloat(), s.width.toFloat(), s.height.toFloat()), skikoPaint
                )
            }

            is Ellipse2D -> {
                canvas?.drawOval(
                    Rect.makeXYWH(s.minX.toFloat(), s.minY.toFloat(), s.width.toFloat(), s.height.toFloat()), skikoPaint
                )
            }

            else -> {
                path(s).use { p -> canvas?.drawPath(p, skikoPaint) }
            }
        }
    }

    /**
     * Fills the specified shape with the current `paint`.  There is
     * direct handling for `Rectangle2D`.
     * All other shapes are mapped to a path outline and then filled.
     *
     * @param s  the shape (`null` not permitted).
     *
     * @see .draw
     */
    override fun fill(s: Shape) {
        if (debugLogEnabled) {
            LOGGER.debug("fill({})", s)
        }
        skikoPaint.mode = PaintMode.FILL
        when (s) {
            is Rectangle2D -> {
                if (s.width <= 0.0 || s.height <= 0.0) {
                    return
                }
                canvas?.drawRect(
                    Rect.makeXYWH(s.x.toFloat(), s.y.toFloat(), s.width.toFloat(), s.height.toFloat()), skikoPaint
                )
            }

            is Ellipse2D -> {
                if (s.width <= 0.0 || s.height <= 0.0) {
                    return
                }
                canvas?.drawOval(
                    Rect.makeXYWH(s.minX.toFloat(), s.minY.toFloat(), s.width.toFloat(), s.height.toFloat()), skikoPaint
                )
            }

            is Path2D -> {
                path(s).use { p ->
                    if (s.windingRule == Path2D.WIND_EVEN_ODD) {
                        p.fillMode = PathFillMode.EVEN_ODD
                    } else {
                        p.fillMode = PathFillMode.WINDING
                    }
                    canvas?.drawPath(p, skikoPaint)
                }
            }

            else -> {
                path(s).use { p -> canvas?.drawPath(p, skikoPaint) }
            }
        }
    }

    /**
     * Draws an image with the specified transform. Note that the
     * `observer` is ignored in this implementation.
     *
     * @param img  the image.
     * @param xform  the transform (`null` permitted).
     * @param obs  the image observer (ignored).
     *
     * @return `true` if the image is drawn.
     */
    override fun drawImage(
        img: Image, xform: AffineTransform?, obs: ImageObserver?
    ): Boolean {
        if (debugLogEnabled) {
            LOGGER.debug("drawImage(Image, AffineTransform, ImageObserver)")
        }
        val savedTransform = getTransform()
        if (xform != null) {
            transform(xform)
        }
        val result = drawImage(img, 0, 0, obs)
        if (xform != null) {
            setTransform(savedTransform)
        }
        return result
    }

    /**
     * Draws the image resulting from applying the `BufferedImageOp`
     * to the specified image at the location `(x, y)`.
     *
     * @param img  the image.
     * @param op  the operation (`null` permitted).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    override fun drawImage(img: BufferedImage, op: BufferedImageOp?, x: Int, y: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("drawImage(BufferedImage, BufferedImageOp, {}, {})", x, y)
        }
        var imageToDraw = img
        if (op != null) {
            imageToDraw = op.filter(img, null)
        }
        drawImage(imageToDraw, AffineTransform(1f, 0f, 0f, 1f, x.toFloat(), y.toFloat()), null)
    }

    /**
     * Draws the rendered image. When `img` is `null` this method
     * does nothing.
     *
     * @param img  the image (`null` permitted).
     * @param xform  the transform.
     */
    override fun drawRenderedImage(img: RenderedImage?, xform: AffineTransform) {
        if (debugLogEnabled) {
            LOGGER.debug("drawRenderedImage(RenderedImage, AffineTransform)")
        }
        if (img == null) { // to match the behaviour specified in the JDK
            return
        }
        val bi = convertRenderedImage(img)
        drawImage(bi, xform, null)
    }

    /**
     * Draws the renderable image.
     *
     * @param img  the renderable image.
     * @param xform  the transform.
     */
    override fun drawRenderableImage(
        img: RenderableImage, xform: AffineTransform
    ) {
        if (debugLogEnabled) {
            LOGGER.debug("drawRenderableImage(RenderableImage, AffineTransform xform)")
        }
        val ri = img.createDefaultRendering()
        drawRenderedImage(ri, xform)
    }

    /**
     * Draws a string at `(x, y)`.  The start of the text at the
     * baseline level will be aligned with the `(x, y)` point.
     *
     * @param str  the string (`null` not permitted).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     *
     * @see .drawString
     */
    override fun drawString(str: String, x: Int, y: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("drawString({}, {}, {}", str, x, y)
        }
        drawString(str, x.toFloat(), y.toFloat())
    }

    /**
     * Draws a string at `(x, y)`. The start of the text at the
     * baseline level will be aligned with the `(x, y)` point.
     *
     * @param str  the string (`null` not permitted).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    override fun drawString(str: String, x: Float, y: Float) {
        if (debugLogEnabled) {
            LOGGER.debug("drawString({}, {}, {})", str, x, y)
        }
        skikoPaint.mode = PaintMode.FILL
        canvas?.drawString(str, x, y, skikoFont, skikoPaint)
    }

    /**
     * Draws a string of attributed characters at `(x, y)`.  The
     * call is delegated to
     * [.drawString].
     *
     * @param iterator  an iterator for the characters.
     * @param x  the x-coordinate.
     * @param y  the x-coordinate.
     */
    override fun drawString(iterator: AttributedCharacterIterator, x: Int, y: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("drawString(AttributedCharacterIterator, {}, {}", x, y)
        }
        drawString(iterator, x.toFloat(), y.toFloat())
    }

    /**
     * Draws a string of attributed characters at `(x, y)`.
     *
     * @param iterator  an iterator over the characters (`null` not
     * permitted).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    override fun drawString(
        iterator: AttributedCharacterIterator, x: Float, y: Float
    ) {
        if (debugLogEnabled) {
            LOGGER.debug("drawString(AttributedCharacterIterator, {}, {}", x, y)
        }
        val s = iterator.allAttributeKeys
        if (s.isNotEmpty()) {
            val layout = TextLayout(iterator, getFontRenderContext())
            layout.draw(this, x, y)
        } else {
            val sb = sbStr // not thread-safe
            sb.setLength(0)
            iterator.first()
            var i = iterator.beginIndex
            while (i < iterator.endIndex) {
                sb.append(iterator.current())
                i++
                iterator.next()
            }
            drawString(sb.toString(), x, y)
        }
    }

    /**
     * Draws the specified glyph vector at the location `(x, y)`.
     *
     * @param g  the glyph vector (`null` not permitted).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    override fun drawGlyphVector(g: GlyphVector, x: Float, y: Float) {
        if (debugLogEnabled) {
            LOGGER.debug("drawGlyphVector(GlyphVector, {}, {})", x, y)
        }
        fill(g.getOutline(x, y))
    }

    /**
     * Returns `true` if the rectangle (in device space) intersects
     * with the shape (the interior, if `onStroke` is `false`,
     * otherwise the stroked outline of the shape).
     *
     * @param rect  a rectangle (in device space).
     * @param s the shape.
     * @param onStroke  test the stroked outline only?
     *
     * @return A boolean.
     */
    override fun hit(rect: Rectangle, s: Shape, onStroke: Boolean): Boolean {
        if (debugLogEnabled) {
            LOGGER.debug("hit(Rectangle, Shape, boolean)")
        }
        val ts: Shape? = if (onStroke) {
            createTransformedShape(stroke!!.createStrokedShape(s), false)
        } else {
            createTransformedShape(s, false)
        }
        if (!rect.bounds2D.intersects(ts!!.bounds2D)) {
            return false
        }
        // note: Area class is very slow (especially for rect):
        val a1 = Area(rect)
        val a2 = Area(ts)
        a1.intersect(a2)
        return !a1.isEmpty
    }

    /**
     * Returns the device configuration associated with this
     * `Graphics2D`.
     *
     * @return The device configuration (never `null`).
     */
    override fun getDeviceConfiguration(): GraphicsConfiguration {
        if (deviceConfiguration == null) {
            val w = width
            val h = height
            deviceConfiguration = SkikoGraphicsConfiguration(w, h)
        }
        return deviceConfiguration!!
    }

    /**
     * Sets the composite (only `AlphaComposite` is handled).
     *
     * @param comp  the composite (`null` not permitted).
     *
     * @see .getComposite
     */
    override fun setComposite(comp: Composite?) {
        if (debugLogEnabled) {
            LOGGER.debug("setComposite({})", comp)
        }
        requireNotNull(comp)
        composite = comp
        if (comp is AlphaComposite) {
            skikoPaint.setAlphaf(comp.alpha)
            when (comp.rule) {
                AlphaComposite.CLEAR -> skikoPaint.blendMode = BlendMode.CLEAR
                AlphaComposite.SRC -> skikoPaint.blendMode = BlendMode.SRC
                AlphaComposite.SRC_OVER -> skikoPaint.blendMode = BlendMode.SRC_OVER
                AlphaComposite.DST_OVER -> skikoPaint.blendMode = BlendMode.DST_OVER
                AlphaComposite.SRC_IN -> skikoPaint.blendMode = BlendMode.SRC_IN
                AlphaComposite.DST_IN -> skikoPaint.blendMode = BlendMode.DST_IN
                AlphaComposite.SRC_OUT -> skikoPaint.blendMode = BlendMode.SRC_OUT
                AlphaComposite.DST_OUT -> skikoPaint.blendMode = BlendMode.DST_OUT
                AlphaComposite.DST -> skikoPaint.blendMode = BlendMode.DST
                AlphaComposite.SRC_ATOP -> skikoPaint.blendMode = BlendMode.SRC_ATOP
                AlphaComposite.DST_ATOP -> skikoPaint.blendMode = BlendMode.DST_ATOP
            }
        }
    }

    override fun setPaint(paint: Paint?) {
        if (debugLogEnabled) {
            LOGGER.debug("setPaint({})", paint)
        }
        if (paint == null) {
            return
        }
        if (paintsAreEqual(paint, awtPaint)) {
            return
        }
        awtPaint = paint
        when (paint) {
            is Color -> {
                color = paint
                skikoPaint.shader = Shader.makeColor(paint.rgb)
            }

            is LinearGradientPaint -> {
                val x0 = paint.startPoint.x.toFloat()
                val y0 = paint.startPoint.y.toFloat()
                val x1 = paint.endPoint.x.toFloat()
                val y1 = paint.endPoint.y.toFloat()
                val colors = IntArray(paint.colors.size)
                for (i in paint.colors.indices) {
                    colors[i] = paint.colors[i].rgb
                }
                val fractions = paint.fractions
                val gs: GradientStyle =
                    GradientStyle.DEFAULT.withTileMode(awtCycleMethodToSkikoFilterTileMode(paint.cycleMethod))
                skikoPaint.shader = Shader.makeLinearGradient(x0, y0, x1, y1, colors, fractions, gs)
            }

            is RadialGradientPaint -> {
                val x = paint.centerPoint.x.toFloat()
                val y = paint.centerPoint.y.toFloat()
                val colors = IntArray(paint.colors.size)
                for (i in paint.colors.indices) {
                    colors[i] = paint.colors[i].rgb
                }
                val gs: GradientStyle =
                    GradientStyle.DEFAULT.withTileMode(awtCycleMethodToSkikoFilterTileMode(paint.cycleMethod))
                val fx = paint.focusPoint.x.toFloat()
                val fy = paint.focusPoint.y.toFloat()
                val shader: Shader = if (paint.focusPoint == paint.centerPoint) {
                    Shader.makeRadialGradient(x, y, paint.radius, colors, paint.fractions, gs)
                } else {
                    Shader.makeTwoPointConicalGradient(fx, fy, 0.0f, x, y, paint.radius, colors, paint.fractions, gs)
                }
                skikoPaint.shader = shader
            }

            is GradientPaint -> {
                val x1 = paint.point1.x.toFloat()
                val y1 = paint.point1.y.toFloat()
                val x2 = paint.point2.x.toFloat()
                val y2 = paint.point2.y.toFloat()
                val colors = intArrayOf(paint.color1.rgb, paint.color2.rgb)
                val gs: GradientStyle =
                    if (paint.isCyclic) GradientStyle.DEFAULT.withTileMode(FilterTileMode.MIRROR) else GradientStyle.DEFAULT
                skikoPaint.shader = Shader.makeLinearGradient(x1, y1, x2, y2, colors, null as FloatArray?, gs)
            }
        }
    }

    /**
     * Sets the stroke that will be used to draw shapes. A `null` stroke is not permitted,
     * but for strict compatibility this should throw an `IllegalArgumentException` so
     * Kotlin's type system isn't used to enforce this.
     *
     * @param s  the stroke (`null` not permitted).
     *
     * @see .getStroke
     */
    override fun setStroke(s: Stroke?) {
        if (debugLogEnabled) {
            LOGGER.debug("setStroke({})", stroke)
        }
        requireNotNull(s) { "Null '$s' argument." }
        if (s === stroke) { // quick test, full equals test later
            return
        }
        if (stroke is BasicStroke) {
            val bs = s as BasicStroke
            if (bs == stroke) {
                return  // no change
            }
            skikoPaint.strokeWidth = max(bs.lineWidth.toDouble(), MIN_LINE_WIDTH).toFloat()
            skikoPaint.strokeCap = awtToSkikoLineCap(bs.endCap)
            skikoPaint.strokeJoin = awtToSkikoLineJoin(bs.lineJoin)
            skikoPaint.strokeMiter = bs.miterLimit
            val dashes = bs.dashArray
            if (dashes != null) {
                try {
                    skikoPaint.pathEffect = PathEffect.makeDash(dashes, bs.dashPhase)
                } catch (re: RuntimeException) {
                    System.err.println("Unable to create skiko paint for dashes: " + Arrays.toString(dashes))
                    re.printStackTrace(System.err)
                    skikoPaint.pathEffect = null
                }
            } else {
                skikoPaint.pathEffect = null
            }
        }
        stroke = s
    }

    /**
     * Maps a line cap code from AWT to the corresponding Skiko `PaintStrokeCap`
     * enum value.
     *
     * @param c  the line cap code.
     *
     * @return A Skiko stroke cap value.
     */
    private fun awtToSkikoLineCap(c: Int): PaintStrokeCap {
        return when (c) {
            BasicStroke.CAP_BUTT -> PaintStrokeCap.BUTT
            BasicStroke.CAP_ROUND -> PaintStrokeCap.ROUND
            BasicStroke.CAP_SQUARE -> PaintStrokeCap.SQUARE
            else -> throw IllegalArgumentException("Unrecognised cap code: $c")
        }
    }

    /**
     * Maps a line join code from AWT to the corresponding Skiko
     * `PaintStrokeJoin` enum value.
     *
     * @param j  the line join code.
     *
     * @return A Skiko stroke join value.
     */
    private fun awtToSkikoLineJoin(j: Int): PaintStrokeJoin {
        return when (j) {
            BasicStroke.JOIN_BEVEL -> PaintStrokeJoin.BEVEL
            BasicStroke.JOIN_MITER -> PaintStrokeJoin.MITER
            BasicStroke.JOIN_ROUND -> PaintStrokeJoin.ROUND
            else -> throw IllegalArgumentException("Unrecognised join code: $j")
        }
    }

    /**
     * Maps a linear gradient paint cycle method from AWT to the corresponding Skiko
     * `FilterTileMode` enum value.
     *
     * @param method  the cycle method.
     *
     * @return A Skiko stroke join value.
     */
    private fun awtCycleMethodToSkikoFilterTileMode(method: MultipleGradientPaint.CycleMethod): FilterTileMode {
        return when (method) {
            MultipleGradientPaint.CycleMethod.NO_CYCLE -> FilterTileMode.CLAMP
            MultipleGradientPaint.CycleMethod.REPEAT -> FilterTileMode.REPEAT
            MultipleGradientPaint.CycleMethod.REFLECT -> FilterTileMode.MIRROR
            else -> FilterTileMode.CLAMP
        }
    }

    /**
     * Returns the current value for the specified hint.  Note that all hints
     * are currently ignored in this implementation.
     *
     * @param hintKey  the hint key (`null` permitted, but the
     * result will be `null` also in that case).
     *
     * @return The current value for the specified hint
     * (possibly `null`).
     *
     * @see .setRenderingHint
     */
    override fun getRenderingHint(hintKey: RenderingHints.Key?): Any? {
        if (debugLogEnabled) {
            LOGGER.debug("getRenderingHint({})", hintKey)
        }
        return hints[hintKey]
    }

    /**
     * Sets the value for a hint.  See the `FXHints` class for
     * information about the hints that can be used with this implementation.
     *
     * @param hintKey  the hint key (`null` not permitted).
     * @param hintValue  the hint value.
     *
     * @see .getRenderingHint
     */
    override fun setRenderingHint(hintKey: RenderingHints.Key, hintValue: Any) {
        if (debugLogEnabled) {
            LOGGER.debug("setRenderingHint({}, {})", hintKey, hintValue)
        }
        hints[hintKey] = hintValue
    }

    /**
     * Sets the rendering hints to the specified collection.
     *
     * @param hints  the new set of hints (`null` not permitted).
     *
     * @see .getRenderingHints
     */
    override fun setRenderingHints(hints: Map<*, *>) {
        if (debugLogEnabled) {
            LOGGER.debug("setRenderingHints(Map<?, ?>): {}", hints)
        }
        this.hints.clear()
        this.hints.putAll(hints)
    }

    /**
     * Adds all the supplied rendering hints.
     *
     * @param hints  the hints (`null` not permitted).
     */
    override fun addRenderingHints(hints: Map<*, *>) {
        if (debugLogEnabled) {
            LOGGER.debug("addRenderingHints(Map<?, ?>): {}", hints)
        }
        this.hints.putAll(hints)
    }

    /**
     * Returns a copy of the rendering hints.  Modifying the returned copy
     * will have no impact on the state of this `Graphics2D`
     * instance.
     *
     * @return The rendering hints (never `null`).
     *
     * @see .setRenderingHints
     */
    override fun getRenderingHints(): RenderingHints {
        if (debugLogEnabled) {
            LOGGER.debug("getRenderingHints()")
        }
        return hints.clone() as RenderingHints
    }

    private val renderingHintsInternally: RenderingHints
        get() {
            if (debugLogEnabled) {
                LOGGER.debug("getRenderingHintsInternally()")
            }
            return hints
        }

    /**
     * Applies the translation `(tx, ty)`.  This call is delegated
     * to [.translate].
     *
     * @param tx  the x-translation.
     * @param ty  the y-translation.
     *
     * @see .translate
     */
    override fun translate(tx: Int, ty: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("translate({}, {})", tx, ty)
        }
        translate(tx.toDouble(), ty.toDouble())
    }

    /**
     * Applies the translation `(tx, ty)`.
     *
     * @param tx  the x-translation.
     * @param ty  the y-translation.
     */
    override fun translate(tx: Double, ty: Double) {
        if (debugLogEnabled) {
            LOGGER.debug("translate({}, {})", tx, ty)
        }
        transform.translate(tx, ty)
        canvas?.translate(tx.toFloat(), ty.toFloat())
    }

    /**
     * Applies a rotation (anti-clockwise) about `(0, 0)`.
     *
     * @param theta  the rotation angle (in radians).
     */
    override fun rotate(theta: Double) {
        if (debugLogEnabled) {
            LOGGER.debug("rotate({})", theta)
        }
        transform.rotate(theta)
        canvas?.rotate(Math.toDegrees(theta).toFloat())
    }

    /**
     * Applies a rotation (anti-clockwise) about `(x, y)`.
     *
     * @param theta  the rotation angle (in radians).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    override fun rotate(theta: Double, x: Double, y: Double) {
        if (debugLogEnabled) {
            LOGGER.debug("rotate({}, {}, {})", theta, x, y)
        }
        translate(x, y)
        rotate(theta)
        translate(-x, -y)
    }

    /**
     * Applies a scale transformation.
     *
     * @param sx  the x-scaling factor.
     * @param sy  the y-scaling factor.
     */
    override fun scale(sx: Double, sy: Double) {
        if (debugLogEnabled) {
            LOGGER.debug("scale({}, {})", sx, sy)
        }
        transform.scale(sx, sy)
        canvas?.scale(sx.toFloat(), sy.toFloat())
    }

    /**
     * Applies a shear transformation. This is equivalent to the following
     * call to the `transform` method:
     * <br></br><br></br>
     * `transform(AffineTransform.getShearInstance(shx, shy))
     *
     * @param shx  the x-shear factor.
     * @param shy  the y-shear factor.
     */
    override fun shear(shx: Double, shy: Double) {
        if (debugLogEnabled) {
            LOGGER.debug("shear({}, {})", shx, shy)
        }
        transform.shear(shx, shy)
        canvas?.skew(shx.toFloat(), shy.toFloat())
    }

    /**
     * Applies this transform to the existing transform by concatenating it.
     *
     * @param t  the transform (`null` not permitted).
     */
    override fun transform(t: AffineTransform) {
        if (debugLogEnabled) {
            LOGGER.debug("transform(AffineTransform) : {}", t)
        }
        val tx = getTransform()
        tx.concatenate(t)
        setTransform(tx)
    }

    /**
     * Returns a copy of the current transform.
     *
     * @return A copy of the current transform (never `null`).
     *
     * @see .setTransform
     */
    override fun getTransform(): AffineTransform {
        if (debugLogEnabled) {
            LOGGER.debug("getTransform()")
        }
        return transform.clone() as AffineTransform
    }

    private val transformInternally: AffineTransform
        get() {
            if (debugLogEnabled) {
                LOGGER.debug("getTransformInternally()")
            }
            return transform
        }

    /**
     * Sets the transform.
     *
     * @param t  the new transform (`null` permitted, resets to the
     * identity transform).
     *
     * @see .getTransform
     */
    override fun setTransform(t: AffineTransform?) {
        if (debugLogEnabled) {
            LOGGER.debug("setTransform({})", t)
        }
        val tt = if (t == null) {
            AffineTransform()
        } else {
            AffineTransform(t)
        }
        transform = tt
        val m33 = Matrix33(
            tt.scaleX.toFloat(),
            tt.shearX.toFloat(),
            tt.translateX.toFloat(),
            tt.shearY.toFloat(),
            tt.scaleY.toFloat(),
            tt.translateY.toFloat(),
            0f,
            0f,
            1f
        )
        canvas?.setMatrix(m33)
    }

    override fun getPaint(): Paint {
        return awtPaint!!
    }

    /**
     * Returns the current composite.
     *
     * @return The current composite (never `null`).
     *
     * @see .setComposite
     */
    override fun getComposite(): Composite {
        return composite
    }

    /**
     * Returns the background color (the default value is [Color.BLACK]).
     * This attribute is used by the [.clearRect]
     * method.
     *
     * @return The background color (possibly `null`).
     *
     * @see .setBackground
     */
    override fun getBackground(): Color? {
        return background
    }

    /**
     * Sets the background color.  This attribute is used by the
     * [.clearRect] method.  The reference
     * implementation allows `null` for the background color so
     * we allow that too (but for that case, the [.clearRect]
     * method will do nothing).
     *
     * @param color  the color (`null` permitted).
     *
     * @see .getBackground
     */
    override fun setBackground(color: Color?) {
        background = color
    }

    /**
     * Returns the current stroke (this attribute is used when drawing shapes).
     *
     * @return The current stroke (never `null`).
     *
     * @see .setStroke
     */
    override fun getStroke(): Stroke {
        return stroke!!
    }

    /**
     * Returns the font render context.
     *
     * @return The font render context (never `null`).
     */
    override fun getFontRenderContext(): FontRenderContext {
        return fontRenderContext
    }

    /**
     * Creates a new graphics object that is a copy of this graphics object.
     *
     * @return A new graphics object.
     */
    override fun create(): Graphics {
        if (debugLogEnabled) {
            LOGGER.debug("create()")
        }
        // use special copy-constructor:
        return SkikoGraphics2D(this)
    }

    /**
     * Returns the foreground color.  This method exists for backwards
     * compatibility in AWT, you should use the [.getPaint] method.
     * This attribute is updated by the [.setColor]
     * method, and also by the [.setPaint] method if
     * a `Color` instance is passed to the method.
     *
     * @return The foreground color (never `null`).
     *
     * @see .getPaint
     */
    override fun getColor(): Color {
        return color!!
    }

    /**
     * Sets the foreground color.  This method exists for backwards
     * compatibility in AWT, you should use the
     * [.setPaint] method.
     *
     * @param c  the color (`null` permitted but ignored).
     *
     * @see .setPaint
     */
    override fun setColor(c: Color?) {
        if (debugLogEnabled) {
            LOGGER.debug("setColor(Color) : {}", c)
        }
        if (c == null || c == color) {
            return
        }
        color = c
        paint = c
    }

    /**
     * Not implemented - the method does nothing.
     */
    override fun setPaintMode() {
        // not implemented
    }

    /**
     * Not implemented - the method does nothing.
     */
    override fun setXORMode(c1: Color) {
        // not implemented
    }

    /**
     * Returns the current font used for drawing text.
     *
     * @return The current font (never `null`).
     *
     * @see .setFont
     */
    override fun getFont(): Font {
        return awtFont
    }

    private fun awtFontStyleToSkikoFontStyle(style: Int): FontStyle {
        return when (style) {
            Font.PLAIN -> FontStyle.NORMAL
            Font.BOLD -> FontStyle.BOLD
            Font.ITALIC -> FontStyle.ITALIC
            Font.BOLD + Font.ITALIC -> FontStyle.BOLD_ITALIC
            else -> FontStyle.NORMAL
        }
    }

    /**
     * Sets the font to be used for drawing text.
     *
     * @param font  the font (`null` is permitted but ignored).
     *
     * @see .getFont
     */
    override fun setFont(font: Font?) {
        if (debugLogEnabled) {
            LOGGER.debug("setFont({})", font)
        }
        if (font == null) {
            return
        }
        awtFont = font
        var fontName = font.name
        // check if there is a font name mapping to apply
        val fm = getRenderingHint(SkikoHints.KEY_FONT_MAPPING_FUNCTION) as Function<String, String?>?
        if (fm != null) {
            val mappedFontName = fm.apply(fontName)
            if (mappedFontName != null) {
                if (debugLogEnabled) {
                    LOGGER.debug("Mapped font name is {}", mappedFontName)
                }
                fontName = mappedFontName
            }
        }
        val style = awtFontStyleToSkikoFontStyle(font.style)
        val key = TypefaceKey(fontName, style)
        typeface = TYPEFACE_MAP[key]
        if (typeface == null) {
            if (debugLogEnabled) {
                LOGGER.debug("Typeface.makeFromName({} style={})", fontName, style)
            }
            typeface = Typeface.makeFromName(fontName, style)
            TYPEFACE_MAP[key] = typeface
        }
        skikoFont = org.jetbrains.skia.Font(typeface, font.size.toFloat())
    }

    /**
     * Returns the font metrics for the specified font.
     *
     * @param f  the font.
     *
     * @return The font metrics.
     */
    override fun getFontMetrics(f: Font): FontMetrics {
        return SkikoFontMetrics(skikoFont, awtFont)
    }

    /**
     * Returns the bounds of the user clipping region.
     *
     * @return The clip bounds (possibly `null`).
     *
     * @see .getClip
     */
    override fun getClipBounds(): Rectangle? {
        return if (clip == null) {
            null
        } else clipInternally!!.bounds
    }

    /**
     * Returns the user clipping region.  The initial default value is
     * `null`.
     *
     * @return The user clipping region (possibly `null`).
     *
     * @see .setClip
     */
    override fun getClip(): Shape? {
        if (debugLogEnabled) {
            LOGGER.debug("getClip()")
        }
        return if (clip == null) {
            null
        } else inverseTransform(clip, true)
    }

    private val clipInternally: Shape?
        get() = if (clip == null) {
            null
        } else inverseTransform(clip, false)

    /**
     * Clips to the intersection of the current clipping region and the
     * specified rectangle.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     */
    override fun clipRect(x: Int, y: Int, width: Int, height: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("clipRect({}, {}, {}, {})", x, y, width, height)
        }
        clip(rect(x, y, width, height))
    }

    /**
     * Sets the user clipping region to the specified rectangle.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     *
     * @see .getClip
     */
    override fun setClip(x: Int, y: Int, width: Int, height: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("setClip({}, {}, {}, {})", x, y, width, height)
        }
        setClip(rect(x, y, width, height))
    }

    /**
     * Sets the user clipping region.
     *
     * @param shape  the new user clipping region (`null` permitted).
     *
     * @see .getClip
     */
    override fun setClip(shape: Shape?) {
        setClip(shape, true)
    }

    private fun setClip(shape: Shape?, clone: Boolean) {
        if (debugLogEnabled) {
            LOGGER.debug("setClip({})", shape)
        }
        // a new clip is being set, so first restore the original clip (and save
        // it again for future restores)
        canvas?.restoreToCount(restoreCount)
        restoreCount = canvas!!.save()
        // restoring the clip might also reset the transform, so reapply it
        setTransform(getTransform())
        // null is handled fine here...
        clip = createTransformedShape(shape, clone) // device space
        // now apply on the Skiko canvas
        if (shape != null) {
            canvas?.clipPath(path(shape))
        }
    }

    /**
     * Clips to the intersection of the current clipping region and the
     * specified shape.
     *
     * According to the Oracle API specification, this method will accept a
     * `null` argument, but there is an open bug report (since 2004)
     * that suggests this is wrong:
     *
     * [http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6206189](http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6206189)
     *
     * @param s  the clip shape (`null` not permitted).
     */
    override fun clip(s: Shape) {
        var ss = s
        if (debugLogEnabled) {
            LOGGER.debug("clip({})", s)
        }
        if (s is Line2D) {
            ss = s.getBounds2D()
        }
        if (clip == null) {
            setClip(ss)
            return
        }
        val clipUser = clipInternally
        val clipNew: Shape = if (!s.intersects(clipUser!!.bounds2D)) {
            Rectangle2D.Double()
        } else {
            // note: Area class is very slow (especially for rectangles)
            val a1 = Area(ss)
            val a2 = Area(clipUser)
            a1.intersect(a2)
            Path2D.Double(a1)
        }
        setClip(clipNew, false) // in user space
    }

    /**
     * Not yet implemented.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width of the area.
     * @param height  the height of the area.
     * @param dx  the delta x.
     * @param dy  the delta y.
     */
    override fun copyArea(x: Int, y: Int, width: Int, height: Int, dx: Int, dy: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("copyArea({}, {}, {}, {}, {}, {}) - NOT IMPLEMENTED", x, y, width, height, dx, dy)
        }
        // FIXME: implement this, low priority
    }

    /**
     * Draws a line from `(x1, y1)` to `(x2, y2)` using
     * the current `paint` and `stroke`.
     *
     * @param x1  the x-coordinate of the start point.
     * @param y1  the y-coordinate of the start point.
     * @param x2  the x-coordinate of the end point.
     * @param y2  the x-coordinate of the end point.
     */
    override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("drawLine()")
        }
        line.setLine(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble())
        draw(line)
    }

    /**
     * Fills the specified rectangle with the current `paint`.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the rectangle width.
     * @param height  the rectangle height.
     */
    override fun fillRect(x: Int, y: Int, width: Int, height: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("fillRect({}, {}, {}, {})", x, y, width, height)
        }
        fill(rect(x, y, width, height))
    }

    /**
     * Clears the specified rectangle by filling it with the current
     * background color.  If the background color is `null`, this
     * method will do nothing.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     *
     * @see .getBackground
     */
    override fun clearRect(x: Int, y: Int, width: Int, height: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("clearRect({}, {}, {}, {})", x, y, width, height)
        }
        if (getBackground() == null) {
            return  // we can't do anything
        }
        val saved = paint
        paint = getBackground() as Paint
        fillRect(x, y, width, height)
        paint = saved
    }

    /**
     * Sets the attributes of the reusable [Rectangle2D] object that is
     * used by the [SkikoGraphics2D.drawRect] and
     * [SkikoGraphics2D.fillRect] methods.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     *
     * @return A rectangle (never `null`).
     */
    private fun rect(x: Int, y: Int, width: Int, height: Int): Rectangle2D {
        rect.setRect(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble())
        return rect
    }

    /**
     * Draws a rectangle with rounded corners using the current
     * `paint` and `stroke`.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param arcWidth  the arc-width.
     * @param arcHeight  the arc-height.
     *
     * @see .fillRoundRect
     */
    override fun drawRoundRect(
        x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int
    ) {
        if (debugLogEnabled) {
            LOGGER.debug("drawRoundRect({}, {}, {}, {}, {}, {})", x, y, width, height, arcWidth, arcHeight)
        }
        draw(roundRect(x, y, width, height, arcWidth, arcHeight))
    }

    /**
     * Fills a rectangle with rounded corners using the current `paint`.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param arcWidth  the arc-width.
     * @param arcHeight  the arc-height.
     *
     * @see .drawRoundRect
     */
    override fun fillRoundRect(
        x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int
    ) {
        if (debugLogEnabled) {
            LOGGER.debug("fillRoundRect({}, {}, {}, {}, {}, {})", x, y, width, height, arcWidth, arcHeight)
        }
        fill(roundRect(x, y, width, height, arcWidth, arcHeight))
    }

    /**
     * Sets the attributes of the reusable [RoundRectangle2D] object that
     * is used by the [.drawRoundRect] and
     * [.fillRoundRect] methods.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param arcWidth  the arc width.
     * @param arcHeight  the arc height.
     *
     * @return A round rectangle (never `null`).
     */
    private fun roundRect(
        x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int
    ): RoundRectangle2D {

        roundRect.setRoundRect(
            x.toDouble(),
            y.toDouble(),
            width.toDouble(),
            height.toDouble(),
            arcWidth.toDouble(),
            arcHeight.toDouble()
        )
        return roundRect
    }

    /**
     * Draws an oval framed by the rectangle `(x, y, width, height)`
     * using the current `paint` and `stroke`.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     *
     * @see .fillOval
     */
    override fun drawOval(x: Int, y: Int, width: Int, height: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("drawOval({}, {}, {}, {})", x, y, width, height)
        }
        draw(oval(x, y, width, height))
    }

    /**
     * Fills an oval framed by the rectangle `(x, y, width, height)`.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     *
     * @see .drawOval
     */
    override fun fillOval(x: Int, y: Int, width: Int, height: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("fillOval({}, {}, {}, {})", x, y, width, height)
        }
        fill(oval(x, y, width, height))
    }

    /**
     * Returns an [Ellipse2D] object that may be reused (so this instance
     * should be used for short term operations only). See the
     * [.drawOval] and
     * [.fillOval] methods for usage.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     *
     * @return An oval shape (never `null`).
     */
    private fun oval(x: Int, y: Int, width: Int, height: Int): Ellipse2D {
        oval.setFrame(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble())
        return oval
    }

    /**
     * Draws an arc contained within the rectangle
     * `(x, y, width, height)`, starting at `startAngle`
     * and continuing through `arcAngle` degrees using
     * the current `paint` and `stroke`.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param startAngle  the start angle in degrees, 0 = 3 o'clock.
     * @param arcAngle  the angle (anticlockwise) in degrees.
     *
     * @see .fillArc
     */
    override fun drawArc(
        x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int
    ) {
        if (debugLogEnabled) {
            LOGGER.debug("drawArc({}, {}, {}, {}, {}, {})", x, y, width, height, startAngle, arcAngle)
        }
        draw(arc(x, y, width, height, startAngle, arcAngle))
    }

    /**
     * Fills an arc contained within the rectangle
     * `(x, y, width, height)`, starting at `startAngle`
     * and continuing through `arcAngle` degrees, using
     * the current `paint`.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param startAngle  the start angle in degrees, 0 = 3 o'clock.
     * @param arcAngle  the angle (anticlockwise) in degrees.
     *
     * @see .drawArc
     */
    override fun fillArc(
        x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int
    ) {
        if (debugLogEnabled) {
            LOGGER.debug("fillArc({}, {}, {}, {}, {}, {})", x, y, width, height, startAngle, arcAngle)
        }
        fill(arc(x, y, width, height, startAngle, arcAngle))
    }

    /**
     * Sets the attributes of the reusable [Arc2D] object that is used by
     * [.drawArc] and
     * [.fillArc] methods.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param startAngle  the start angle in degrees, 0 = 3 o'clock.
     * @param arcAngle  the angle (anticlockwise) in degrees.
     *
     * @return An arc (never `null`).
     */
    private fun arc(
        x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int
    ): Arc2D {
        arc.setArc(
            x.toDouble(),
            y.toDouble(),
            width.toDouble(),
            height.toDouble(),
            startAngle.toDouble(),
            arcAngle.toDouble(),
            Arc2D.OPEN
        )
        return arc
    }

    /**
     * Draws the specified multi-segment line using the current
     * `paint` and `stroke`.
     *
     * @param xPoints  the x-points.
     * @param yPoints  the y-points.
     * @param nPoints  the number of points to use for the polyline.
     */
    override fun drawPolyline(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("drawPolyline(int[], int[], int)")
        }
        draw(createPolygon(xPoints, yPoints, nPoints, false))
    }

    /**
     * Draws the specified polygon using the current `paint` and
     * `stroke`.
     *
     * @param xPoints  the x-points.
     * @param yPoints  the y-points.
     * @param nPoints  the number of points to use for the polygon.
     *
     * @see .fillPolygon
     */
    override fun drawPolygon(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("drawPolygon(int[], int[], int)")
        }
        draw(createPolygon(xPoints, yPoints, nPoints, true))
    }

    /**
     * Fills the specified polygon using the current `paint`.
     *
     * @param xPoints  the x-points.
     * @param yPoints  the y-points.
     * @param nPoints  the number of points to use for the polygon.
     *
     * @see .drawPolygon
     */
    override fun fillPolygon(xPoints: IntArray, yPoints: IntArray, nPoints: Int) {
        if (debugLogEnabled) {
            LOGGER.debug("fillPolygon(int[], int[], int)")
        }
        fill(createPolygon(xPoints, yPoints, nPoints, true))
    }

    /**
     * Creates a polygon from the specified `x` and
     * `y` coordinate arrays.
     *
     * @param xPoints  the x-points.
     * @param yPoints  the y-points.
     * @param nPoints  the number of points to use for the polyline.
     * @param close  closed?
     *
     * @return A polygon.
     */
    private fun createPolygon(
        xPoints: IntArray, yPoints: IntArray, nPoints: Int, close: Boolean
    ): GeneralPath {
        if (debugLogEnabled) {
            LOGGER.debug("createPolygon(int[], int[], int, boolean)")
        }
        val p = GeneralPath()
        p.moveTo(xPoints[0].toFloat(), yPoints[0].toFloat())
        for (i in 1 until nPoints) {
            p.lineTo(xPoints[i].toFloat(), yPoints[i].toFloat())
        }
        if (close) {
            p.closePath()
        }
        return p
    }

    /**
     * Draws an image at the location `(x, y)`.  Note that the
     * `observer` is ignored.
     *
     * @param img  the image (`null` permitted...method will do nothing).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param observer  ignored.
     *
     * @return `true` if there is no more drawing to be done.
     */
    override fun drawImage(img: Image?, x: Int, y: Int, observer: ImageObserver?): Boolean {
        if (debugLogEnabled) {
            LOGGER.debug("drawImage(Image, {}, {}, ImageObserver)", x, y)
        }
        if (img == null) {
            return true
        }
        val w = img.getWidth(observer)
        if (w < 0) {
            return false
        }
        val h = img.getHeight(observer)
        return if (h < 0) {
            false
        } else drawImage(img, x, y, w, h, observer)
    }

    /**
     * Draws the image into the rectangle defined by `(x, y, w, h)`.
     * Note that the `observer` is ignored (it is not useful in this
     * context).
     *
     * @param img  the image (`null` permitted...draws nothing).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param observer  ignored.
     *
     * @return `true` if there is no more drawing to be done.
     */
    override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver?): Boolean {
        if (debugLogEnabled) {
            LOGGER.debug("drawImage(Image, {}, {}, {}, {}, ImageObserver)", x, y, width, height)
        }
        // LOGGER.info("drawImage(Image, {}, {}, {}, {}, ImageObserver)", x, y, width, height);
        val buffered: BufferedImage
        if (img is BufferedImage && img.type == BufferedImage.TYPE_INT_ARGB) {
            buffered = img
        } else {
            // LOGGER.info("drawImage(): copy BufferedImage");
            buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val g2 = buffered.createGraphics()
            g2.drawImage(img, 0, 0, width, height, null)
            g2.dispose()
        }
        convertToSkikoImage(buffered).use { skikoImage ->
            canvas?.drawImageRect(
                skikoImage, Rect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
            )
        }
        return true
    }

    /**
     * Draws an image at the location `(x, y)`.  Note that the
     * `observer` is ignored.
     *
     * @param img  the image (`null` permitted...draws nothing).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param bgcolor  the background color (`null` permitted).
     * @param observer  ignored.
     *
     * @return `true` if there is no more drawing to be done.
     */
    override fun drawImage(
        img: Image?, x: Int, y: Int, bgcolor: Color?, observer: ImageObserver?
    ): Boolean {
        if (debugLogEnabled) {
            LOGGER.debug("drawImage(Image, {}, {}, Color, ImageObserver)", x, y)
        }
        if (img == null) {
            return true
        }
        val w = img.getWidth(null)
        if (w < 0) {
            return false
        }
        val h = img.getHeight(null)
        return if (h < 0) {
            false
        } else drawImage(img, x, y, w, h, bgcolor, observer)
    }

    override fun drawImage(
        img: Image?, x: Int, y: Int, width: Int, height: Int, bgcolor: Color?, observer: ImageObserver?
    ): Boolean {
        if (debugLogEnabled) {
            LOGGER.debug("drawImage(Image, {}, {}, {}, {}, Color, ImageObserver)", x, y, width, height)
        }
        bgcolor?.let {
            val saved = paint
            paint = it
            fillRect(x, y, width, height)
            paint = saved
        }
        return drawImage(img, x, y, width, height, observer)
    }

    /**
     * Draws part of an image (defined by the source rectangle
     * `(sx1, sy1, sx2, sy2)`) into the destination rectangle
     * `(dx1, dy1, dx2, dy2)`.  Note that the `observer`
     * is ignored in this implementation.
     *
     * @param img  the image.
     * @param dx1  the x-coordinate for the top left of the destination.
     * @param dy1  the y-coordinate for the top left of the destination.
     * @param dx2  the x-coordinate for the bottom right of the destination.
     * @param dy2  the y-coordinate for the bottom right of the destination.
     * @param sx1  the x-coordinate for the top left of the source.
     * @param sy1  the y-coordinate for the top left of the source.
     * @param sx2  the x-coordinate for the bottom right of the source.
     * @param sy2  the y-coordinate for the bottom right of the source.
     *
     * @return `true` if the image is drawn.
     */
    override fun drawImage(
        img: Image?,
        dx1: Int,
        dy1: Int,
        dx2: Int,
        dy2: Int,
        sx1: Int,
        sy1: Int,
        sx2: Int,
        sy2: Int,
        observer: ImageObserver?
    ): Boolean {
        if (debugLogEnabled) {
            LOGGER.debug(
                "drawImage(Image, {}, {}, {}, {}, {}, {}, {}, {}, ImageObserver)",
                dx1,
                dy1,
                dx2,
                dy2,
                sx1,
                sy1,
                sx2,
                sy2
            )
        }
        val w = dx2 - dx1
        val h = dy2 - dy1
        val img2 = BufferedImage(
            w, h, BufferedImage.TYPE_INT_ARGB
        )
        val g2 = img2.createGraphics()
        g2.drawImage(img, 0, 0, w, h, sx1, sy1, sx2, sy2, null)
        return drawImage(img2, dx1, dy1, null)
    }

    /**
     * Draws part of an image (defined by the source rectangle
     * `(sx1, sy1, sx2, sy2)`) into the destination rectangle
     * `(dx1, dy1, dx2, dy2)`.  The destination rectangle is first
     * cleared by filling it with the specified `bgcolor`. Note that
     * the `observer` is ignored.
     *
     * @param img  the image.
     * @param dx1  the x-coordinate for the top left of the destination.
     * @param dy1  the y-coordinate for the top left of the destination.
     * @param dx2  the x-coordinate for the bottom right of the destination.
     * @param dy2  the y-coordinate for the bottom right of the destination.
     * @param sx1 the x-coordinate for the top left of the source.
     * @param sy1 the y-coordinate for the top left of the source.
     * @param sx2 the x-coordinate for the bottom right of the source.
     * @param sy2 the y-coordinate for the bottom right of the source.
     * @param bgcolor  the background color (`null` permitted).
     * @param observer  ignored.
     *
     * @return `true` if the image is drawn.
     */
    override fun drawImage(
        img: Image?,
        dx1: Int,
        dy1: Int,
        dx2: Int,
        dy2: Int,
        sx1: Int,
        sy1: Int,
        sx2: Int,
        sy2: Int,
        bgcolor: Color?,
        observer: ImageObserver?
    ): Boolean {
        if (debugLogEnabled) {
            LOGGER.debug(
                "drawImage(Image, {}, {}, {}, {}, {}, {}, {}, {}, Color, ImageObserver)",
                dx1,
                dy1,
                dx2,
                dy2,
                sx1,
                sy1,
                sx2,
                sy2
            )
        }
        bgcolor?.let {
            val saved = paint
            paint = it
            fillRect(dx1, dy1, dx2 - dx1, dy2 - dy1)
            paint = saved
        }
        return drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
    }

    /**
     * This method does nothing.
     */
    override fun dispose() {
        if (debugLogEnabled) {
            LOGGER.debug("dispose()")
        }
        canvas?.restoreToCount(restoreCount)
    }

    private fun createTransformedShape(s: Shape?, clone: Boolean): Shape? {
        return if (transform.isIdentity) {
            if (clone) cloneShape(s) else s
        } else transform.createTransformedShape(s)
    }

    private fun inverseTransform(s: Shape?, clone: Boolean): Shape? {
        return if (transform.isIdentity) {
            if (clone) cloneShape(s) else s
        } else try {
            transform.createInverse().createTransformedShape(s)
        } catch (nite: NoninvertibleTransformException) {
            null
        }
    }

    private fun cloneShape(s: Shape?): Shape? {
        if (s == null) {
            return null
        }
        if (s is Rectangle2D) {
            return Rectangle2D.Double().also {
                it.setRect(s)
            }
        }
        return Path2D.Double(s, null)
    }

    companion object {
        /** SkikoGraphics2D version  */
        val VERSION: String = Version.version
        private val LOGGER = LoggerFactory.getLogger(SkikoGraphics2D::class.java)

        /** The line width to use when a BasicStroke with line width = 0.0 is applied.  */
        private const val MIN_LINE_WIDTH = 0.1

        /** default paint  */
        private val DEFAULT_COLOR = Color.BLACK

        /** default stroke  */
        private val DEFAULT_STROKE: Stroke = BasicStroke(1.0f)

        /** default font  */
        private val DEFAULT_FONT = Font("SansSerif", Font.PLAIN, 12)

        /** font mapping  */
        private val FONT_MAPPING = createDefaultFontMap()
        private val TYPEFACE_MAP = mutableMapOf<TypefaceKey, Typeface?>()

        /**
         * The font render context.  The fractional metrics flag solves the glyph
         * positioning issue identified by Christoph Nahr:
         * http://news.kynosarges.org/2014/06/28/glyph-positioning-in-jfreesvg-orsonpdf/
         */
        private val DEFAULT_FONT_RENDER_CONTEXT = FontRenderContext(
            null, false, true
        )

        /**
         * Creates a map containing default mappings from the Java logical font names
         * to suitable physical font names.  This is not a particularly great solution,
         * but so far I don't see a better alternative.
         *
         * @return A map.
         */
        private fun createDefaultFontMap(): Map<String, String?> {
            val result: MutableMap<String, String?> = HashMap(8)
            val os = System.getProperty("os.name").lowercase(Locale.getDefault())
            if (os.contains("win")) { // Windows
                result[Font.MONOSPACED] = "Courier New"
                result[Font.SANS_SERIF] = "Arial"
                result[Font.SERIF] = "Times New Roman"
            } else if (os.contains("mac")) { // MacOS
                result[Font.MONOSPACED] = "Courier New"
                result[Font.SANS_SERIF] = "Helvetica"
                result[Font.SERIF] = "Times New Roman"
            } else { // assume Linux
                result[Font.MONOSPACED] = "Courier New"
                result[Font.SANS_SERIF] = "Arial"
                result[Font.SERIF] = "Times New Roman"
            }
            result[Font.DIALOG] = result[Font.SANS_SERIF]
            result[Font.DIALOG_INPUT] = result[Font.SANS_SERIF]
            return result
        }

        /**
         * Returns `true` if the two `Paint` objects are equal
         * OR both `null`.  This method handles
         * `GradientPaint`, `LinearGradientPaint`
         * and `RadialGradientPaint` as special cases, since those classes do
         * not override the `equals()` method.
         *
         * @param p1  paint 1 (`null` permitted).
         * @param p2  paint 2 (`null` permitted).
         *
         * @return A boolean.
         */
        private fun paintsAreEqual(p1: Paint?, p2: Paint?): Boolean {
            if (p1 === p2) {
                return true
            }

            // handle cases where either or both arguments are null
            if (p1 == null) {
                return p2 == null
            }
            if (p2 == null) {
                return false
            }

            // handle cases...
            if (p1 is Color && p2 is Color) {
                return p1 == p2
            }
            if (p1 is GradientPaint && p2 is GradientPaint) {
                return p1.color1 == p2.color1 && p1.color2 == p2.color2 && p1.point1 == p2.point1 && p1.point2 == p2.point2 && p1.isCyclic == p2.isCyclic && p1.transparency == p1.transparency
            }
            if (p1 is LinearGradientPaint && p2 is LinearGradientPaint) {
                return (p1.startPoint == p2.startPoint && p1.endPoint == p2.endPoint && Arrays.equals(
                    p1.fractions, p2.fractions
                ) && Arrays.equals(
                    p1.colors, p2.colors
                ) && p1.cycleMethod == p2.cycleMethod) && p1.colorSpace == p2.colorSpace && p1.transform == p2.transform
            }
            if (p1 is RadialGradientPaint && p2 is RadialGradientPaint) {
                return (p1.centerPoint == p2.centerPoint && p1.radius == p2.radius && p1.focusPoint == p2.focusPoint && Arrays.equals(
                    p1.fractions, p2.fractions
                ) && Arrays.equals(
                    p1.colors, p2.colors
                ) && p1.cycleMethod == p2.cycleMethod) && p1.colorSpace == p2.colorSpace && p1.transform == p2.transform
            }
            return p1 == p2
        }

        /**
         * Converts a rendered image to a `BufferedImage`.  This utility
         * method has come from a forum post by Jim Moore at:
         *
         * [http://www.jguru.com/faq/view.jsp?EID=114602](http://www.jguru.com/faq/view.jsp?EID=114602)
         *
         * @param img  the rendered image.
         *
         * @return A buffered image.
         */
        private fun convertRenderedImage(img: RenderedImage): BufferedImage {
            if (img is BufferedImage) {
                return img
            }
            val width = img.width
            val height = img.height
            val cm = img.colorModel
            val isAlphaPremultiplied = cm.isAlphaPremultiplied
            val raster = cm.createCompatibleWritableRaster(width, height)
            val properties: Hashtable<Any, Any?> = Hashtable<Any, Any?>()
            val keys = img.propertyNames
            if (keys != null) {
                for (key in keys) {
                    properties[key] = img.getProperty(key)
                }
            }
            val result = BufferedImage(cm, raster, isAlphaPremultiplied, properties)
            img.copyData(raster)
            return result
        }

        private fun convertToSkikoImage(image: BufferedImage): org.jetbrains.skia.Image {
            // TODO: monitor performance:
            val w = image.width
            val h = image.height
            val db = image.raster.dataBuffer as java.awt.image.DataBufferInt
            val pixels = db.data
            val bytes = ByteArray(pixels.size * 4) // Big alloc!
            for (i in pixels.indices) {
                val p = pixels[i]
                bytes[i * 4 + 3] = (p and -0x1000000 shr 24).toByte()
                bytes[i * 4 + 2] = (p and 0xFF0000 shr 16).toByte()
                bytes[i * 4 + 1] = (p and 0xFF00 shr 8).toByte()
                bytes[i * 4] = (p and 0xFF).toByte()
            }
            val imageInfo = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)

            // LOGGER.info("convertToSkikoImage(): {}", imageInfo);
            return org.jetbrains.skia.Image.makeRaster(imageInfo, bytes, 4 * w)
        }
    }
}

data class TypefaceKey(val fontName: String, val style: FontStyle)
