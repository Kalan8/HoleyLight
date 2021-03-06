/*
 * Copyright (C) 2019 Jorrit "Chainfire" Jongma
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package eu.chainfire.holeylight.misc;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.view.Display;

import java.util.List;

import androidx.core.view.WindowInsetsCompat;

@SuppressWarnings({"WeakerAccess", "unused"})
public class CameraCutout {
    public static class Cutout {
        private final Rect area;
        private final Point resolution;

        public Cutout(Rect area, Point resolution) {
            this.area = area;
            this.resolution = resolution;
        }

        public Cutout(Cutout src) {
            this.area = new Rect(src.getArea());
            this.resolution = new Point(src.getResolution());
        }

        public Rect getArea() { return area; }
        public Point getResolution() { return resolution; }

        public Cutout scaleTo(Point resolution) {
            if (this.resolution.equals(resolution)) return this;
            float sX = (float)resolution.x / (float)this.resolution.x;
            float sY = (float)resolution.y / (float)this.resolution.y;
            return new Cutout(new Rect(
                    (int)((float)area.left * sX),
                    (int)((float)area.top * sY),
                    (int)((float)area.right * sX),
                    (int)((float)area.bottom * sY)
            ), resolution);
        }

        public boolean equalsScaled(Cutout cmp) {
            // scaling and rounding introduces errors, allow 2 pixel discrepancy
            if (cmp == null) return false;
            Cutout a = this;
            Cutout b = cmp;
            if (!a.getResolution().equals(b.getResolution())) {
                if (a.getResolution().x * a.getResolution().y > b.getResolution().x * b.getResolution().y) {
                    b = b.scaleTo(a.getResolution());
                } else {
                    a = a.scaleTo(b.getResolution());
                }
            }
            Rect rA = a.getArea();
            Rect rB = b.getArea();
            return
                    Math.abs(rA.left - rB.left) <= 2 &&
                    Math.abs(rA.top - rB.top) <= 2 &&
                    Math.abs(rA.right - rB.right) <= 2 &&
                    Math.abs(rA.bottom - rB.bottom) <= 2;                          
        }

        public boolean isCircular() {
            return Math.abs(area.width() - area.height()) <= 2;
        }
    }

    // these were determined by running on each devices, algorithm seems perfect for S10/S10E,
    // but has a few extra pixels on the right for S10PLUS.
    public static final Cutout CUTOUT_S10E = new Cutout(new Rect(931, 25, 1021, 116), new Point(1080, 2280));
    public static final Cutout CUTOUT_S10 = new Cutout(new Rect(1237, 33, 1352, 149), new Point(1440, 3040));
    public static final Cutout CUTOUT_S10PLUS = new Cutout(new Rect(1114, 32, 1378, 142), new Point(1440, 3040));

    private final Display display;
    private final int nativeMarginTop;
    private final int nativeMarginRight;

    private Cutout cutout = null;

    public CameraCutout(Context context) {
        this.display = ((DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(0);

        int id;
        Resources res = context.getResources();

        // below is Samsung S10(?) specific. Newer firmwares on other Samsung devices also seem to have these values present.

        id = res.getIdentifier("status_bar_camera_top_margin", "dimen", "android");
        nativeMarginTop = id > 0 ? res.getDimensionPixelSize(id) : 0;

        id = res.getIdentifier("status_bar_camera_padding", "dimen", "android");
        nativeMarginRight = id > 0 ? res.getDimensionPixelSize(id) : 0;
    }

    public Point getNativeResolution() {
        Point ret = null;

        Display.Mode[] modes = display.getSupportedModes();
        for (Display.Mode mode : modes) {
            if ((ret == null) || (mode.getPhysicalWidth() > ret.x) || (mode.getPhysicalHeight() > ret.y)) {
                ret = new Point(mode.getPhysicalWidth(), mode.getPhysicalHeight());
            }
        }

        return ret;
    }

    public Point getCurrentResolution() {
        Point ret = new Point();
        display.getRealSize(ret);
        return ret;
    }

    public void updateFromBoundingRect(Rect rect) {
        Point nativeRes = getNativeResolution();
        Point currentRes = getCurrentResolution();

        Rect r = new Rect(rect);

        // convert margins from native to current resolution, and apply to rect; without this we'd get a big notch rather than just the camera area
        r.right -= (int)((float)nativeMarginRight * ((float)currentRes.x / (float)nativeRes.x));
        r.top += (int)((float)nativeMarginTop * ((float)currentRes.y / (float)nativeRes.y));

        cutout = new Cutout(r, currentRes);
    }

    public void updateFromAreaRect(Rect rect) {
        cutout = new Cutout(rect, getCurrentResolution());
    }

    public void updateFromInsets(WindowInsetsCompat insets) {
        if ((insets == null) || (insets.getDisplayCutout() == null)) return;
        List<Rect> rects = insets.getDisplayCutout().getBoundingRects();
        if (rects.size() != 1) return;
        updateFromBoundingRect(rects.get(0));
    }

    public Cutout getCutout() {
        return cutout == null ? null : new Cutout(cutout);
    }

    public void applyCutout(Cutout cutout) {
        this.cutout = cutout.scaleTo(getCurrentResolution());
    }

    public boolean isValid() {
        return cutout != null;
    }
}
