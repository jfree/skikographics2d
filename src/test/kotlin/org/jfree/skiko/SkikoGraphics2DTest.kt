/* ===================
 * SkikoGraphics2DTest
 * ===================
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

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.test.*

/**
 * Some tests for a Graphics2D implementation.  All tests should pass with the
 * Graphics2D instance from a BufferedImage (which we can treat as a reference
 * implementation).
 */
class SkikoGraphics2DTest {

    private var g2: Graphics2D = SkikoGraphics2D(10, 20)

    @BeforeEach
    fun setUp() {
        g2 = if (TEST_REFERENCE_IMPLEMENTATION) {
            // to test a reference implementation, use this Graphics2D from a
            // BufferedImage in the JDK
            BufferedImage(10, 20, BufferedImage.TYPE_INT_ARGB).createGraphics()
        } else {
            // Test SkikoGraphics2D...
            SkikoGraphics2D(10, 20)
        }
    }

    /**
     * Checks that the default transform is an identity transform.
     */
    @Test
    fun checkDefaultTransform() {
        assertEquals(AffineTransform(), g2.transform)
    }

    /**
     * Modifying the transform returned by the Graphics2D should not affect
     * the state of the Graphics2D.  In order for that to happen, the method
     * should be returning a copy of the actual transform object.
     */
    @Test
    fun checkGetTransformSafety() {
        val t = g2.transform
        t.rotate(Math.PI)
        assertNotEquals(t, g2.transform)
        assertEquals(AffineTransform(), g2.transform)
    }

    /**
     * A basic check that setTransform() does indeed update the transform.
     */
    @Test
    fun setTransform() {
        val t = AffineTransform(1f, 2f, 3f, 4f, 5f, 6f)
        g2.transform = t
        assertEquals(t, g2.transform)
        t.setTransform(6.0, 5.0, 4.0, 3.0, 2.0, 1.0)
        g2.transform = t
        assertEquals(t, g2.transform)

        // in spite of the docs saying that null is accepted this gives
        // a NullPointerException with SunGraphics2D.
        //g2.setTransform(null);
        //assertEquals(new AffineTransform(), g2.getTransform());
    }

    /**
     * When calling setTransform() the caller passes in an AffineTransform
     * instance.  If the caller retains a reference to the AffineTransform
     * and subsequently modifies it, we don't want the Graphics2D object to
     * be affected...so it should be making an internal copy of the
     * AffineTransform.
     */
    @Test
    fun checkSetTransformSafety() {
        val t = AffineTransform.getTranslateInstance(1.0, 2.0)
        g2.transform = t
        assertEquals(t, g2.transform)
        t.setToRotation(Math.PI)
        assertNotEquals(t, g2.transform)
    }

    @Test
    fun checkSetNonInvertibleTransform() {
        val t = AffineTransform.getScaleInstance(0.0, 0.0)
        g2.transform = t
        assertEquals(t, g2.transform)

        // after setting the clip, we cannot retrieve it while the transform
        // is non-invertible...
        val clip: Rectangle2D = Rectangle2D.Double(1.0, 2.0, 3.0, 4.0)
        g2.clip = clip
        assertNull(g2.clip)
        g2.transform = AffineTransform()
        assertEquals(
            Rectangle2D.Double(0.0, 0.0, 0.0, 0.0),
            g2.clip.bounds2D
        )
    }

    /**
     * A check for a call to transform() with a rotation, that follows a
     * translation.
     */
    @Test
    fun checkTransform() {
        var t = AffineTransform()
        g2.transform = t
        g2.translate(30, 30)
        val rt = AffineTransform.getRotateInstance(
            Math.PI / 2.0,
            300.0, 200.0
        )
        g2.transform(rt)
        t = g2.transform
        assertEquals(0.0, t.scaleX, EPSILON)
        assertEquals(0.0, t.scaleY, EPSILON)
        assertEquals(-1.0, t.shearX, EPSILON)
        assertEquals(1.0, t.shearY, EPSILON)
        assertEquals(530.0, t.translateX, EPSILON)
        assertEquals(-70.0, t.translateY, EPSILON)
    }

    @Test
    fun checkTransformNull() {
        try {
            g2.transform(null)
            fail("Expected a NullPointerException.")
        } catch (e: NullPointerException) {
            // this exception is expected
        }
    }

    /**
     * Basic checks for the scale(x, y) method.
     */
    @Test
    fun scale() {
        g2.scale(0.5, 2.0)
        assertEquals(
            AffineTransform.getScaleInstance(0.5, 2.0),
            g2.transform
        )
        g2.scale(2.0, -1.0)
        assertEquals(
            AffineTransform.getScaleInstance(1.0, -2.0),
            g2.transform
        )
    }

    /**
     * Checks that a call to scale(x, y) on top of an existing translation
     * gives the correct values.
     */
    @Test
    fun translateFollowedByScale() {
        g2.translate(2, 3)
        assertEquals(
            AffineTransform.getTranslateInstance(2.0, 3.0),
            g2.transform
        )
        g2.scale(10.0, 20.0)
        assertEquals(
            AffineTransform(10.0, 0.0, 0.0, 20.0, 2.0, 3.0),
            g2.transform
        )
    }

    /**
     * Checks that a call to translate(x, y) on top of an existing scale
     * gives the correct values.
     */
    @Test
    fun scaleFollowedByTranslate() {
        g2.scale(2.0, 2.0)
        assertEquals(
            AffineTransform.getScaleInstance(2.0, 2.0),
            g2.transform
        )
        g2.translate(10, 20)
        assertEquals(
            AffineTransform(2.0, 0.0, 0.0, 2.0, 20.0, 40.0),
            g2.transform
        )
    }

    @Test
    fun scaleFollowedByRotate() {
        g2.scale(2.0, 2.0)
        assertEquals(
            AffineTransform.getScaleInstance(2.0, 2.0),
            g2.transform
        )
        g2.rotate(Math.PI / 3)
        val t = g2.transform
        assertEquals(1.0, t.scaleX, EPSILON)
        assertEquals(1.0, t.scaleY, EPSILON)
        assertEquals(-1.7320508075688772, t.shearX, EPSILON)
        assertEquals(1.7320508075688772, t.shearY, EPSILON)
        assertEquals(0.0, t.translateX, EPSILON)
        assertEquals(0.0, t.translateY, EPSILON)
    }

    @Test
    fun rotateFollowedByScale() {
        g2.rotate(Math.PI)
        assertEquals(
            AffineTransform.getRotateInstance(Math.PI),
            g2.transform
        )
        g2.scale(2.0, 2.0)
        assertEquals(
            AffineTransform(-2.0, 0.0, 0.0, -2.0, 0.0, 0.0),
            g2.transform
        )
    }

    /**
     * Checks that the getClip() method returns a different object than what
     * was passed to setClip(), and that multiple calls to getClip() return
     * a new object each time.
     */
    @Test
    fun checkGetClipSafety() {
        val r: Rectangle2D = Rectangle2D.Double(0.0, 0.0, 1.0, 1.0)
        g2.clip = r
        val s = g2.clip
        assertNotSame(r, s)
        val s2 = g2.clip
        assertNotSame(s, s2)
    }

    /**
     * The default user clip should be `null`.
     */
    @Test
    fun checkDefaultClip() {
        assertNull(g2.clip, "Default user clip should be null.")
    }

    /**
     * Checks that getClipBounds() is returning an integer approximation of
     * the bounds.
     */
    @Test
    fun checkGetClipBounds() {
        val r: Rectangle2D = Rectangle2D.Double(0.25, 0.25, 0.5, 0.5)
        g2.clip = r
        assertEquals(Rectangle(0, 0, 1, 1), g2.clipBounds)
    }

    /**
     * Checks that getClipBounds() returns `null` when the clip is
     * `null`.
     */
    @Test
    fun checkGetClipBoundsWhenClipIsNull() {
        g2.clip = null
        assertNull(g2.clipBounds)
    }

    /**
     * Simple check that the clip() methods creates an intersection with the
     * existing clip region.
     */
    @Test
    fun checkClip() {
        val r: Rectangle2D = Rectangle2D.Double(1.0, 1.0, 3.0, 3.0)
        g2.clip = r
        g2.clip(Rectangle2D.Double(0.0, 0.0, 2.0, 2.0))
        assertEquals(
            Rectangle2D.Double(1.0, 1.0, 1.0, 1.0),
            g2.clip.bounds2D
        )
    }

    /**
     * Check that if the user clip is non-intersecting with the existing clip, then
     * the clip is empty.
     */
    @Test
    fun checkNonIntersectingClip() {
        val r: Rectangle2D = Rectangle2D.Double(1.0, 1.0, 3.0, 3.0)
        g2.clip = r
        g2.clip(Rectangle2D.Double(5.0, 5.0, 1.0, 1.0))
        assertTrue(g2.clip.bounds2D.isEmpty)
    }

    /**
     * After applying a scale transformation, getClip() will return a
     * modified clip.
     */
    @Test
    fun checkClipAfterScaling() {
        var r: Rectangle2D = Rectangle2D.Double(1.0, 2.0, 3.0, 0.5)
        g2.clip = r
        assertEquals(r, g2.clip.bounds2D)
        g2.scale(0.5, 2.0)
        assertEquals(
            Rectangle2D.Double(2.0, 1.0, 6.0, 0.25),
            g2.clip.bounds2D
        )

        // check that we get a good intersection when clipping after the
        // scaling has been done...
        r = Rectangle2D.Double(3.0, 0.0, 2.0, 2.0)
        g2.clip(r)
        assertEquals(
            Rectangle2D.Double(3.0, 1.0, 2.0, 0.25),
            g2.clip.bounds2D
        )
    }

    /**
     * Translating will change the existing clip.
     */
    @Test
    fun checkClipAfterTranslate() {
        val clip: Rectangle2D = Rectangle2D.Double(0.0, 0.0, 1.0, 1.0)
        g2.clip = clip
        assertEquals(clip, g2.clip.bounds2D)
        g2.translate(1.0, 2.0)
        assertEquals(
            Rectangle(-1, -2, 1, 1),
            g2.clip.bounds2D
        )
    }

    @Test
    fun checkSetClipAfterTranslate() {
        g2.translate(1.0, 2.0)
        g2.setClip(0, 0, 1, 1)
        assertEquals(Rectangle(0, 0, 1, 1), g2.clip.bounds)
        g2.translate(1.0, 2.0)
        assertEquals(Rectangle(-1, -2, 1, 1), g2.clip.bounds)
    }

    /**
     * Transforming will change the reported clipping shape.
     */
    @Test
    fun checkClipAfterTransform() {
        val clip: Rectangle2D = Rectangle2D.Double(0.0, 0.0, 1.0, 1.0)
        g2.clip = clip
        assertEquals(clip, g2.clip.bounds2D)
        g2.transform(AffineTransform.getRotateInstance(Math.PI))
        assertEquals(
            Rectangle(-1, -1, 1, 1),
            g2.clip.bounds2D
        )
        g2.transform = AffineTransform()
        assertEquals(clip, g2.clip.bounds2D)
    }

    /**
     * Clipping with a line makes no sense, but the API allows it so we should
     * not fail.  In fact, running with a JDK Graphics2D (from a BufferedImage)
     * it seems that the bounding rectangle of the line is used for clipping...
     * does that make sense?  Switching off the test for now.
     */
    @Test
    @Disabled
    fun checkClipWithLine2D() {
        val r: Rectangle2D = Rectangle2D.Double(1.0, 1.0, 3.0, 3.0)
        g2.clip = r
        g2.clip(Line2D.Double(1.0, 2.0, 3.0, 4.0))
        //assertEquals(new Rectangle2D.Double(1.0, 2.0, 2.0, 2.0),
        //        this.g2.getClip().getBounds2D());
        //assertTrue(this.g2.getClip().getBounds2D().isEmpty());
    }

    /**
     * A simple check for a call to clipRect().
     */
    @Test
    fun checkClipRect() {
        val clip: Rectangle2D = Rectangle2D.Double(0.0, 0.0, 5.0, 5.0)
        g2.clip = clip
        g2.clipRect(2, 1, 4, 2)
        assertEquals(
            Rectangle(2, 1, 3, 2),
            g2.clip.bounds2D
        )
    }

    @Test
    fun checkClipRectParams() {
        val clip: Rectangle2D = Rectangle2D.Double(0.0, 0.0, 5.0, 5.0)
        g2.clip = clip

        // negative width
        g2.clipRect(2, 1, -4, 2)
        assertTrue(g2.clip.bounds2D.isEmpty)

        // negative height
        g2.clip = clip
        g2.clipRect(2, 1, 4, -2)
        assertTrue(g2.clip.bounds2D.isEmpty)
    }

    @Test
    fun checkDrawStringWithEmptyString() {
        // this should not cause any exception
        g2.drawString("", 1, 2)
    }

    /**
     * Some checks for the create() method.
     */
    @Test
    fun checkCreate() {
        g2.clip = Rectangle(1, 2, 3, 4)
        val copy = g2.create() as Graphics2D
        assertEquals(copy.background, g2.background)
        assertEquals(
            copy.clip.bounds2D,
            g2.clip.bounds2D
        )
        assertEquals(copy.color, g2.color)
        assertEquals(copy.composite, g2.composite)
        assertEquals(copy.font, g2.font)
        assertEquals(copy.renderingHints, g2.renderingHints)
        assertEquals(copy.stroke, g2.stroke)
        assertEquals(copy.transform, g2.transform)
    }

    /**
     * The setPaint() method allows a very minor state leakage in the sense
     * that it is possible to modify a GradientPaint externally after a call
     * to the setPaint() method and have it impact the state of the
     * Graphics2D implementation.  Avoiding this would require cloning the
     * Paint object, but there is no good way to do that for an arbitrary
     * Paint instance.
     */
    @Test
    fun checkSetPaintSafety() {
        val pt1: Point2D = Point2D.Double(1.0, 2.0)
        val pt2: Point2D = Point2D.Double(3.0, 4.0)
        val gp = GradientPaint(pt1, Color.RED, pt2, Color.BLUE)
        g2.paint = gp
        assertEquals(gp, g2.paint)
        assertSame(gp, g2.paint)
        pt1.setLocation(7.0, 7.0)
        assertEquals(gp, g2.paint)
    }

    /**
     * According to the Javadocs, setting the paint to null should have no
     * impact on the current paint (that is, the call is silently ignored).
     */
    @Test
    fun checkSetPaintNull() {
        g2.paint = Color.RED
        // this next call should have no impact
        g2.paint = null
        assertEquals(Color.RED, g2.paint)
    }

    /**
     * Passing a Color to setPaint() also updates the color, but not the
     * background color.
     */
    @Test
    fun checkSetPaintAlsoUpdatesColorButNotBackground() {
        val existingBackground = g2.background
        g2.paint = Color.MAGENTA
        assertEquals(Color.MAGENTA, g2.paint)
        assertEquals(Color.MAGENTA, g2.color)
        assertEquals(existingBackground, g2.background)
    }

    /**
     * If setPaint() is called with an argument that is not an instance of
     * Color, then the existing color remains unchanged.
     */
    @Test
    fun checkSetPaintDoesNotUpdateColor() {
        val gp = GradientPaint(
            1.0f, 2.0f, Color.RED,
            3.0f, 4.0f, Color.BLUE
        )
        g2.color = Color.MAGENTA
        g2.paint = gp
        assertEquals(gp, g2.paint)
        assertEquals(Color.MAGENTA, g2.color)
    }

    /**
     * Verifies that setting the old AWT color attribute also updates the
     * Java2D paint attribute.
     *
     * @see .checkSetPaintAlsoUpdatesColorButNotBackground
     */
    @Test
    fun checkSetColorAlsoUpdatesPaint() {
        g2.color = Color.MAGENTA
        assertEquals(Color.MAGENTA, g2.paint)
        assertEquals(Color.MAGENTA, g2.color)
    }

    /**
     * The behaviour of the reference implementation has been observed as
     * ignoring null.  This matches the documented behaviour of the
     * setPaint() method.
     */
    @Test
    fun checkSetColorNull() {
        g2.color = Color.RED
        g2.color = null
        assertEquals(Color.RED, g2.color)
    }

    /**
     * Setting the background color does not change the color or paint.
     */
    @Test
    fun checkSetBackground() {
        g2.background = Color.CYAN
        assertEquals(Color.CYAN, g2.background)
        assertNotEquals(Color.CYAN, g2.color)
        assertNotEquals(Color.CYAN, g2.paint)
    }

    /**
     * The behaviour of the reference implementation has been observed as
     * allowing null (this is inconsistent with the behaviour of setColor()).
     */
    @Test
    fun checkSetBackgroundNull() {
        g2.background = Color.RED
        g2.background = null
        assertNull(g2.background)
    }

    /**
     * Since the setBackground() method is allowing null, we should ensure
     * that the clearRect() method doesn't fail in this case.  With no
     * background color, the clearRect() method should be a no-op but there
     * is no easy way to test for that.
     */
    @Test
    fun checkClearRectWithNullBackground() {
        g2.background = null
        g2.clearRect(1, 2, 3, 4)
        //no exceptions and we're good
    }

    /**
     * In the reference implementation, setting a null composite has been
     * observed to throw an IllegalArgumentException.
     */
    @Test
    fun checkSetCompositeNull() {
        try {
            g2.composite = null
            fail("Expected an IllegalArgumentException.")
        } catch (e: IllegalArgumentException) {
            // this exception is expected in the test
        }
    }

    @Test
    fun checkSetStrokeNull() {
        try {
            g2.stroke = null
            fail("Expected an IllegalArgumentException.")
        } catch (e: IllegalArgumentException) {
            // this exception is expected in the test
        }
    }

    /**
     * Basic check of set then get.
     */
    @Test
    fun checkSetRenderingHint() {
        g2.setRenderingHint(
            RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_PURE
        )
        assertEquals(
            RenderingHints.VALUE_STROKE_PURE,
            g2.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL)
        )
    }

    /**
     * The reference implementation has been observed to throw a
     * NullPointerException when the key is null.
     */
    @Test
    fun checkSetRenderingHintWithNullKey() {
        try {
            g2.setRenderingHint(null, "XYZ")
            fail("NullPointerException is expected here.")
        } catch (e: NullPointerException) {
            // this is expected
        }
    }

    /**
     * The reference implementation has been observed to accept a null key
     * and return null in that case.
     */
    @Test
    fun checkGetRenderingHintWithNullKey() {
        assertNull(g2.getRenderingHint(null))
    }

    /**
     * Check setting a hint with a value that doesn't match the key.
     */
    @Test
    fun checkSetRenderingHintWithInconsistentValue() {
        try {
            g2.setRenderingHint(
                RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_ANTIALIAS_DEFAULT
            )
            fail("Expected an IllegalArgumentException.")
        } catch (e: IllegalArgumentException) {
            // we expect this exception
        }
    }

    /**
     * A call to getRenderingHints() is returning a copy of the hints, so
     * changing it will not affect the state of the Graphics2D instance.
     */
    @Test
    fun checkGetRenderingHintsSafety() {
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_OFF
        )
        val hints = g2.renderingHints
        hints[RenderingHints.KEY_ANTIALIASING] = RenderingHints.VALUE_ANTIALIAS_ON
        assertEquals(
            RenderingHints.VALUE_ANTIALIAS_OFF,
            g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        )
    }

    @Test
    fun checkSetRenderingHintsNull() {
        try {
            g2.setRenderingHints(null)
            fail("NullPointerException expected.")
        } catch (e: NullPointerException) {
            // this is expected
        }
    }

    @Test
    fun checkHit() {
        val shape: Shape = Rectangle2D.Double(0.0, 0.0, 1.0, 1.0)
        val r = Rectangle(2, 2, 2, 2)
        assertFalse(g2.hit(r, shape, false))
        g2.scale(3.0, 3.0)
        assertTrue(g2.hit(r, shape, false))
    }

    @Test
    fun checkHitForOutline() {
        val shape: Shape = Rectangle2D.Double(0.0, 0.0, 3.0, 3.0)
        val r = Rectangle(1, 1, 1, 1)
        assertFalse(g2.hit(r, shape, true))
        g2.scale(0.5, 0.5)
        // now the rectangle is entirely inside the shape, but does not touch
        // the outline...
        assertTrue(g2.hit(r, shape, true))
    }

    /**
     * We have observed in the reference implementation that setting the font
     * to null does not change the current font setting.
     */
    @Test
    fun checkSetFontNull() {
        val f = Font("Serif", Font.PLAIN, 8)
        g2.font = f
        assertEquals(f, g2.font)
        g2.font = null
        assertEquals(f, g2.font)
    }

    @Test
    fun checkDefaultStroke() {
        val s = g2.stroke as BasicStroke
        assertEquals(BasicStroke.CAP_SQUARE, s.endCap)
        assertEquals(1.0, s.lineWidth.toDouble(), EPSILON)
        assertEquals(BasicStroke.JOIN_MITER, s.lineJoin)
    }

    /**
     * Check that a null GlyphVector throws a `NullPointerException`.
     */
    @Test
    fun drawGlyphVectorNull() {
        try {
            g2.drawGlyphVector(null, 10f, 10f)
            fail("Expecting a NullPointerException.")
        } catch (e: NullPointerException) {
            // expected
        }
    }

    /**
     * Check the shear() method.
     */
    @Test
    fun shear() {
        g2.transform = AffineTransform()
        g2.shear(2.0, 3.0)
        assertEquals(AffineTransform(1f, 3f, 2f, 1f, 0f, 0f), g2.transform)
    }

    /**
     * Checks a translate() followed by a shear().
     */
    @Test
    fun shearFollowingTranslate() {
        g2.transform = AffineTransform()
        g2.translate(10.0, 20.0)
        g2.shear(2.0, 3.0)
        assertEquals(AffineTransform(1f, 3f, 2f, 1f, 10f, 20f), g2.transform)
    }

    @Test
    fun drawImageWithNullBackground() {
        val img: Image = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
        g2.drawImage(img, 10, 10, null, null)
        assertTrue(true) // won't get here if there's an exception above
    }

    /**
     * https://github.com/jfree/jfreesvg/issues/6
     */
    @Test
    fun drawImageWithNullTransform() {
        val img: Image = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
        g2.drawImage(img, null, null)
        assertTrue(true) // won't get here if there's an exception above
    }

    @Test
    fun drawImageWithNullImage() {
        // API docs say method does nothing if img is null
        // still seems to return true
        assertTrue(g2.drawImage(null, 10, 20, null))
        assertTrue(g2.drawImage(null, 10, 20, 30, 40, null))
        assertTrue(g2.drawImage(null, 10, 20, Color.YELLOW, null))
        assertTrue(g2.drawImage(null, 1, 2, 3, 4, Color.RED, null))
        assertTrue(g2.drawImage(null, 1, 2, 3, 4, 5, 6, 7, 8, null))
        assertTrue(g2.drawImage(null, 1, 2, 3, 4, 5, 6, 7, 8, Color.RED, null))
    }

    @Test
    fun drawImageWithNegativeDimensions() {
        val img: Image = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
        assertTrue(g2.drawImage(img, 1, 2, -10, 10, null))
        assertTrue(g2.drawImage(img, 1, 2, 10, -10, null))
    }

    /**
     * Check that the color is not changed by setting a clip.  In some
     * implementations the clip is saved/restored as part of the overall
     * graphics state so clipping can impact other attributes.
     */
    @Test
    fun checkColorAfterSetClip() {
        g2.color = Color.RED
        assertEquals(Color.RED, g2.color)
        g2.setClip(0, 0, 10, 10)
        assertEquals(Color.RED, g2.color)
        g2.color = Color.BLUE
        assertEquals(Color.BLUE, g2.color)
        g2.setClip(0, 0, 20, 20)
        assertEquals(Color.BLUE, g2.color)
    }

    /**
     * See https://github.com/jfree/fxgraphics2d/issues/6
     */
    @Test
    fun checkFontAfterSetClip() {
        g2.font = Font(Font.DIALOG, Font.BOLD, 12)
        assertEquals(Font(Font.DIALOG, Font.BOLD, 12), g2.font)
        g2.setClip(0, 0, 10, 10)
        assertEquals(Font(Font.DIALOG, Font.BOLD, 12), g2.font)
        g2.font = Font(Font.DIALOG, Font.BOLD, 24)
        assertEquals(Font(Font.DIALOG, Font.BOLD, 24), g2.font)
        g2.setClip(0, 0, 20, 20)
        assertEquals(Font(Font.DIALOG, Font.BOLD, 24), g2.font)
    }

    /**
     * See https://github.com/jfree/fxgraphics2d/issues/6
     */
    @Test
    fun checkStrokeAfterSetClip() {
        g2.stroke = BasicStroke(1.0f)
        assertEquals(BasicStroke(1.0f), g2.stroke)
        g2.setClip(0, 0, 10, 10)
        assertEquals(BasicStroke(1.0f), g2.stroke)
        g2.stroke = BasicStroke(2.0f)
        assertEquals(BasicStroke(2.0f), g2.stroke)
        g2.setClip(0, 0, 20, 20)
        assertEquals(BasicStroke(2.0f), g2.stroke)
    }

    /**
     * A test to check whether setting a transform on the Graphics2D affects
     * the results of text measurements performed via getFontMetrics().
     */
    @Test
    fun testGetFontMetrics() {
        val f = Font(Font.SANS_SERIF, Font.PLAIN, 10)
        var fm = g2.getFontMetrics(f)
        val w = fm.stringWidth("ABC")
        val bounds = fm.getStringBounds("ABC", g2)

        // after scaling, the string width is not changed
        g2.transform = AffineTransform.getScaleInstance(3.0, 2.0)
        fm = g2.getFontMetrics(f)
        assertEquals(w, fm.stringWidth("ABC"))
        assertEquals(bounds.width, fm.getStringBounds("ABC", g2).width, EPSILON)
    }

    @Test
    fun drawImageWithNullImageOp() {
        val img = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
        g2.drawImage(img, null, 2, 3)
        assertTrue(true) // won't get here if there's an exception above
    }

    /**
     * API docs say the method does nothing when called with a null image.
     */
    @Test
    fun drawRenderedImageWithNullImage() {
        g2.drawRenderedImage(null, AffineTransform.getTranslateInstance(0.0, 0.0))
        assertTrue(true) // won't get here if there's an exception above
    }

    /**
     * Filling and/or stroking a Rectangle2D with a negative width will not display anything but
     * should not throw an exception.
     */
    @Test
    fun fillOrStrokeRectangleWithNegativeWidthMustNotFail() {
        g2.draw(Rectangle2D.Double(0.0, 0.0, 0.0, 10.0))
        g2.draw(Rectangle2D.Double(0.0, 0.0, -10.0, 10.0))
        g2.fill(Rectangle2D.Double(0.0, 0.0, 0.0, 10.0))
        g2.fill(Rectangle2D.Double(0.0, 0.0, -10.0, 10.0))
        assertTrue(true) // won't get here if there's an exception above
    }

    /**
     * Filling and/or stroking a Rectangle2D with a negative height will not display anything but
     * should not throw an exception.
     */
    @Test
    fun fillOrStrokeRectangleWithNegativeHeightMustNotFail() {
        g2.draw(Rectangle2D.Double(0.0, 0.0, 0.0, 10.0))
        g2.draw(Rectangle2D.Double(0.0, 0.0, -10.0, 10.0))
        g2.fill(Rectangle2D.Double(0.0, 0.0, 0.0, 10.0))
        g2.fill(Rectangle2D.Double(0.0, 0.0, -10.0, 10.0))
        assertTrue(true) // won't get here if there's an exception above
    }

    @Test
    fun checkClipAfterCreate() {
        g2.setClip(10, 20, 30, 40)
        assertEquals(Rectangle(10, 20, 30, 40), g2.clip.bounds2D)
        val g2copy = g2.create() as Graphics2D
        g2copy.clipRect(11, 21, 10, 10)
        assertEquals(Rectangle(11, 21, 10, 10), g2copy.clip.bounds2D)
        g2copy.dispose()
        assertEquals(Rectangle(10, 20, 30, 40), g2.clip.bounds2D)
    }

    companion object {
        /**
         * Change this to true to test against a reference Graphics2D
         * implementation from the JDK.  This is useful to verify that the tests
         * are correct.
         */
        private const val TEST_REFERENCE_IMPLEMENTATION = false
        private const val EPSILON = 0.000000001
    }
}