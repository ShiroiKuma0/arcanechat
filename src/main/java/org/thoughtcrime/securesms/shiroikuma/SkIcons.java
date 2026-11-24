package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;


/**
 * shiroikuma fork: the line icons of the black-yellow look, drawn in code on a 24-unit grid - a
 * drawn icon follows any colour role without a tint pass, and no vector asset has to be added for
 * a row that upstream left without an icon. Ported from 白い熊 雷起動盤's {@code UiIcons}.
 *
 * <p>An icon is coloured by what its row DOES ({@link Does}): a menu is read by colour before it
 * is read by word, so the same colour means the same thing on every surface.
 */
public final class SkIcons {

    private SkIcons() {
    }

    public enum Icon {
        EDIT, DELETE, COPY, EXPORT, IMPORT, ADD, CLOSE, CHECK, UP, DOWN, LEFT, RIGHT, SLIDERS, PALETTE,
        TEXT, SIZE, LIST, KEYBOARD, LOCK, UNLOCK, GLOBE, DEVICE, INFO, RESET, PLAY, EYE, LAYERS, BRANCH,
        BOOK, BLOCK, TOGGLE, MOVE, CODE, DIRECTORY, HISTORY, STAR, PRIVACY, SPARK, POINTER, GAUGE,
        // the launcher's own
        GRID, WIDGET, IMAGE, SEARCH, HOME, PIN, ARCHIVE, SHARE, STORE, SORT, SELECT, APP, GEAR, HELP,
        FRAME, STOP, DESKTOP, LINK, CHEVRON,
        // the messenger's own
        PERSON, GROUP, PHONE, BUBBLE, BELL, MUTE, CLOCK, QR, CAMERA, MIC, ATTACH, REPLY, FORWARD, SAVE,
        VIDEO, MAIL, WARNING, FLASH, TICK2
    }

    /** What a row does — the role its icon is coloured by. */
    public enum Does {
        /** Views, opens a page, settings: the accent. */
        VIEW,
        /** Runs, launches, adds. */
        CONNECT,
        /** Edits, renames, writes. */
        MESSAGE,
        /** Is about an item, an app, a widget. */
        CONTACT,
        /** Copies, moves, exports. */
        SHARE,
        /** Locks, hides, stops. */
        BLOCK,
        /** Deletes, removes. */
        DELETE
    }

    public static int color(Context context, Does does) {
        switch (does) {
            case CONNECT:
                return SkRoles.get(context, SkRole.ICON_CONNECT);
            case MESSAGE:
                return SkRoles.get(context, SkRole.ICON_MESSAGE);
            case CONTACT:
                return SkRoles.get(context, SkRole.ICON_CONTACT);
            case SHARE:
                return SkRoles.get(context, SkRole.ICON_SHARE);
            case BLOCK:
                return SkRoles.get(context, SkRole.ICON_BLOCK);
            case DELETE:
                return SkRoles.get(context, SkRole.ICON_DELETE);
            default:
                return SkRoles.get(context, SkRole.ICON_VIEW);
        }
    }

    public static Drawable drawable(Context context, Icon icon, Does does) {
        return drawable(context, icon, color(context, does), 20f);
    }

    public static Drawable drawable(Context context, Icon icon, int color, float sizeDp) {
        int size = Math.round(sizeDp * context.getResources().getDisplayMetrics().density);
        IconDrawable d = new IconDrawable(icon, color, size);
        d.setBounds(0, 0, size, size);
        return d;
    }

    /**
     * The size of a row's icon: as tall as the label's text line (1.25 x its text size), and never
     * under 18 dp - the font is settable, so the icon follows it.
     */
    public static int labelSize(android.widget.TextView label) {
        float d = label.getResources().getDisplayMetrics().density;
        return Math.max(Math.round(18 * d), Math.round(label.getTextSize() * 1.25f));
    }

    /**
     * Put {@code icon} at the start of {@code label}, sized by the label, with an optional chevron
     * at its end. The size is read again whenever the label is laid out, so a font pass that runs
     * later is followed.
     */
    public static void onLabel(final android.widget.TextView label, final Icon icon, final int color,
                               final boolean chevron, final int chevronColor) {
        if (label == null) {
            return;
        }
        Object old = label.getTag(org.thoughtcrime.securesms.R.id.sk_icon);
        if (old instanceof android.view.View.OnLayoutChangeListener) {
            label.removeOnLayoutChangeListener((android.view.View.OnLayoutChangeListener) old);
        }
        if (icon == null && !chevron) {
            label.setCompoundDrawablesRelative(null, null, null, null);
            label.setTag(org.thoughtcrime.securesms.R.id.sk_icon, null);
            return;
        }
        final int[] applied = {0};
        final Runnable apply = new Runnable() {
            @Override
            public void run() {
                int size = labelSize(label);
                if (size == applied[0]) {
                    return;
                }
                applied[0] = size;
                Drawable start = null, end = null;
                if (icon != null) {
                    start = new IconDrawable(icon, color, size);
                    start.setBounds(0, 0, size, size);
                }
                if (chevron) {
                    end = new IconDrawable(Icon.CHEVRON, chevronColor, size);
                    end.setBounds(0, 0, size, size);
                }
                label.setCompoundDrawablesRelative(start, null, end, null);
                label.setCompoundDrawablePadding(size / 2);
            }
        };
        apply.run();
        android.view.View.OnLayoutChangeListener l = new android.view.View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(android.view.View v, int a, int b, int c, int d, int e, int f, int g, int h) {
                apply.run();
            }
        };
        label.addOnLayoutChangeListener(l);
        label.setTag(org.thoughtcrime.securesms.R.id.sk_icon, l);
    }

    /** Put {@code icon} at the start of a list row's label — one icon column, one label edge. */
    public static void onRow(android.widget.TextView row, Icon icon, Does does) {
        if (row == null) {
            return;
        }
        Context context = row.getContext();
        row.setCompoundDrawablesRelative(drawable(context, icon, does), null, null, null);
        row.setCompoundDrawablePadding(Math.round(12 * context.getResources().getDisplayMetrics().density));
    }

    private static final class IconDrawable extends Drawable {
        private final Icon icon;
        private final int size;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF box = new RectF();
        private Canvas c;

        IconDrawable(Icon icon, int color, int size) {
            this.icon = icon;
            this.size = size;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(color);
            line.setStrokeWidth(1.8f);
            solid.setStyle(Paint.Style.FILL);
            solid.setColor(color);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            line.setAlpha(alpha);
            solid.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            line.setColorFilter(cf);
            solid.setColorFilter(cf);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / 24f;
            if (s <= 0) {
                return;
            }
            canvas.save();
            canvas.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f);
            canvas.scale(s, s);
            c = canvas;
            paint();
            c = null;
            canvas.restore();
        }

        private void l(float x1, float y1, float x2, float y2) {
            c.drawLine(x1, y1, x2, y2, line);
        }

        private void c(float x, float y, float r) {
            c.drawCircle(x, y, r, line);
        }

        private void dot(float x, float y) {
            dot(x, y, 1.3f);
        }

        private void dot(float x, float y, float r) {
            c.drawCircle(x, y, r, solid);
        }

        private void r(float l, float t, float r, float b) {
            r(l, t, r, b, 2f);
        }

        private void r(float l, float t, float r, float b, float rad) {
            box.set(l, t, r, b);
            c.drawRoundRect(box, rad, rad, line);
        }

        private void arc(float l, float t, float r, float b, float start, float sweep) {
            box.set(l, t, r, b);
            c.drawArc(box, start, sweep, false, line);
        }

        private void poly(float... p) {
            poly(false, p);
        }

        private void closed(float... p) {
            poly(true, p);
        }

        private void poly(boolean close, float... p) {
            path.reset();
            path.moveTo(p[0], p[1]);
            for (int i = 2; i + 1 < p.length; i += 2) {
                path.lineTo(p[i], p[i + 1]);
            }
            if (close) {
                path.close();
            }
            c.drawPath(path, line);
        }

        private void paint() {
            switch (icon) {
                case EDIT:
                    closed(4f, 20f, 5f, 15.5f, 16f, 4.5f, 19.5f, 8f, 8.5f, 19f);
                    l(13.5f, 7f, 17f, 10.5f);
                    break;
                case DELETE:
                    l(4f, 7f, 20f, 7f);
                    poly(9f, 7f, 9.5f, 4f, 14.5f, 4f, 15f, 7f);
                    poly(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f);
                    l(10f, 10.5f, 10.3f, 16.5f);
                    l(14f, 10.5f, 13.7f, 16.5f);
                    break;
                case COPY:
                    r(8.5f, 8.5f, 20f, 20f);
                    poly(15.5f, 8.5f, 15.5f, 4f, 4f, 4f, 4f, 15.5f, 8.5f, 15.5f);
                    break;
                case EXPORT:
                    l(12f, 15f, 12f, 4f);
                    poly(7.5f, 8.5f, 12f, 4f, 16.5f, 8.5f);
                    poly(4f, 14f, 4f, 20f, 20f, 20f, 20f, 14f);
                    break;
                case IMPORT:
                    l(12f, 4f, 12f, 15f);
                    poly(7.5f, 10.5f, 12f, 15f, 16.5f, 10.5f);
                    poly(4f, 14f, 4f, 20f, 20f, 20f, 20f, 14f);
                    break;
                case ADD:
                    l(12f, 5f, 12f, 19f);
                    l(5f, 12f, 19f, 12f);
                    break;
                case CLOSE:
                    l(6f, 6f, 18f, 18f);
                    l(18f, 6f, 6f, 18f);
                    break;
                case CHECK:
                    poly(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f);
                    break;
                case UP:
                    l(12f, 19f, 12f, 5f);
                    poly(6f, 11f, 12f, 5f, 18f, 11f);
                    break;
                case DOWN:
                    l(12f, 5f, 12f, 19f);
                    poly(6f, 13f, 12f, 19f, 18f, 13f);
                    break;
                case LEFT:
                    l(19f, 12f, 5f, 12f);
                    poly(11f, 6f, 5f, 12f, 11f, 18f);
                    break;
                case RIGHT:
                    l(5f, 12f, 19f, 12f);
                    poly(13f, 6f, 19f, 12f, 13f, 18f);
                    break;
                case SLIDERS:
                    l(4f, 7f, 20f, 7f);
                    l(4f, 12f, 20f, 12f);
                    l(4f, 17f, 20f, 17f);
                    dot(9f, 7f, 2.3f);
                    dot(15f, 12f, 2.3f);
                    dot(8f, 17f, 2.3f);
                    break;
                case PALETTE:
                    path.reset();
                    path.moveTo(12f, 3.5f);
                    path.cubicTo(8f, 9f, 5.5f, 11.5f, 5.5f, 14.5f);
                    path.cubicTo(5.5f, 18f, 8.5f, 20.5f, 12f, 20.5f);
                    path.cubicTo(15.5f, 20.5f, 18.5f, 18f, 18.5f, 14.5f);
                    path.cubicTo(18.5f, 11.5f, 16f, 9f, 12f, 3.5f);
                    path.close();
                    c.drawPath(path, line);
                    arc(8.5f, 11f, 15.5f, 18f, 100f, 70f);
                    break;
                case TEXT:
                    poly(5f, 20f, 12f, 4f, 19f, 20f);
                    l(7.8f, 14f, 16.2f, 14f);
                    break;
                case SIZE:
                    l(5f, 19f, 19f, 5f);
                    poly(12.5f, 5f, 19f, 5f, 19f, 11.5f);
                    poly(5f, 12.5f, 5f, 19f, 11.5f, 19f);
                    break;
                case LIST:
                    dot(5f, 7f);
                    dot(5f, 12f);
                    dot(5f, 17f);
                    l(9f, 7f, 20f, 7f);
                    l(9f, 12f, 20f, 12f);
                    l(9f, 17f, 20f, 17f);
                    break;
                case KEYBOARD:
                    r(3f, 6f, 21f, 18f, 2.5f);
                    dot(7f, 10f, 1f);
                    dot(10.3f, 10f, 1f);
                    dot(13.7f, 10f, 1f);
                    dot(17f, 10f, 1f);
                    l(8f, 14.5f, 16f, 14.5f);
                    break;
                case LOCK:
                    r(5.5f, 10.5f, 18.5f, 20f);
                    arc(8f, 4f, 16f, 14f, 180f, 180f);
                    dot(12f, 15f, 1.4f);
                    break;
                case UNLOCK:
                    r(5.5f, 10.5f, 18.5f, 20f);
                    arc(8f, 4f, 16f, 14f, 180f, 130f);
                    l(8f, 9f, 8f, 10.5f);
                    dot(12f, 15f, 1.4f);
                    break;
                case GLOBE:
                    c(12f, 12f, 8.5f);
                    l(3.5f, 12f, 20.5f, 12f);
                    arc(8f, 3.5f, 16f, 20.5f, 0f, 360f);
                    break;
                case DEVICE:
                    r(7f, 3.5f, 17f, 20.5f, 2.5f);
                    l(10.5f, 17.5f, 13.5f, 17.5f);
                    break;
                case INFO:
                    c(12f, 12f, 8.5f);
                    l(12f, 11f, 12f, 16.5f);
                    dot(12f, 7.8f, 1.2f);
                    break;
                case RESET:
                    arc(4.5f, 4.5f, 19.5f, 19.5f, -60f, 300f);
                    poly(11.5f, 2f, 15.8f, 5.5f, 11.5f, 9f);
                    break;
                case PLAY:
                    closed(8f, 5f, 19f, 12f, 8f, 19f);
                    break;
                case EYE:
                    path.reset();
                    path.moveTo(2.5f, 12f);
                    path.quadTo(12f, 2.5f, 21.5f, 12f);
                    path.quadTo(12f, 21.5f, 2.5f, 12f);
                    c.drawPath(path, line);
                    c(12f, 12f, 3f);
                    break;
                case LAYERS:
                    closed(12f, 4f, 21f, 9f, 12f, 14f, 3f, 9f);
                    poly(3f, 13.5f, 12f, 18.5f, 21f, 13.5f);
                    break;
                case BRANCH:
                    c(7f, 6f, 2.2f);
                    c(7f, 18f, 2.2f);
                    c(17f, 8f, 2.2f);
                    l(7f, 8.2f, 7f, 15.8f);
                    arc(-3f, 0f, 17f, 13.5f, 0f, 90f);
                    break;
                case BOOK:
                    r(5f, 4f, 19f, 20f, 1.5f);
                    l(9f, 4f, 9f, 20f);
                    l(12f, 9f, 16f, 9f);
                    l(12f, 12.5f, 16f, 12.5f);
                    break;
                case BLOCK:
                    c(12f, 12f, 8.5f);
                    l(6f, 6f, 18f, 18f);
                    break;
                case TOGGLE:
                    r(3f, 7.5f, 21f, 16.5f, 4.5f);
                    dot(16.5f, 12f, 2.4f);
                    break;
                case MOVE:
                    l(12f, 3.5f, 12f, 20.5f);
                    l(3.5f, 12f, 20.5f, 12f);
                    poly(9.5f, 6f, 12f, 3.5f, 14.5f, 6f);
                    poly(9.5f, 18f, 12f, 20.5f, 14.5f, 18f);
                    poly(6f, 9.5f, 3.5f, 12f, 6f, 14.5f);
                    poly(18f, 9.5f, 20.5f, 12f, 18f, 14.5f);
                    break;
                case CODE:
                    poly(8.5f, 7f, 3.5f, 12f, 8.5f, 17f);
                    poly(15.5f, 7f, 20.5f, 12f, 15.5f, 17f);
                    l(13.5f, 5f, 10.5f, 19f);
                    break;
                case DIRECTORY:
                    closed(3.5f, 6f, 9.5f, 6f, 11.5f, 8.5f, 20.5f, 8.5f, 20.5f, 19f, 3.5f, 19f);
                    break;
                case HISTORY:
                    c(12f, 12f, 8.5f);
                    poly(12f, 7f, 12f, 12f, 15.5f, 14f);
                    break;
                case STAR:
                    closed(12f, 3.5f, 14.6f, 9.2f, 20.8f, 9.8f, 16.1f, 14f, 17.5f, 20.2f, 12f, 17f, 6.5f,
                            20.2f, 7.9f, 14f, 3.2f, 9.8f, 9.4f, 9.2f);
                    break;
                case PRIVACY:
                    closed(12f, 3.5f, 19f, 6.5f, 19f, 12f, 12f, 20.5f, 5f, 12f, 5f, 6.5f);
                    poly(9f, 11.5f, 11.3f, 14f, 15.2f, 9.5f);
                    break;
                case SPARK:
                    closed(12f, 3.5f, 13.8f, 10.2f, 20.5f, 12f, 13.8f, 13.8f, 12f, 20.5f, 10.2f, 13.8f,
                            3.5f, 12f, 10.2f, 10.2f);
                    break;
                case POINTER:
                    closed(6f, 4f, 18f, 12.5f, 12.5f, 13.5f, 9.5f, 19f);
                    break;
                case GAUGE:
                    arc(3.5f, 5.5f, 20.5f, 22.5f, 180f, 180f);
                    l(12f, 14f, 16f, 9f);
                    dot(12f, 14f, 1.5f);
                    break;
                case GRID:
                    r(4f, 4f, 10.5f, 10.5f, 1.2f);
                    r(13.5f, 4f, 20f, 10.5f, 1.2f);
                    r(4f, 13.5f, 10.5f, 20f, 1.2f);
                    r(13.5f, 13.5f, 20f, 20f, 1.2f);
                    break;
                case WIDGET:
                    r(4f, 4f, 20f, 11f, 1.5f);
                    r(4f, 14f, 10.5f, 20f, 1.2f);
                    r(13.5f, 14f, 20f, 20f, 1.2f);
                    break;
                case IMAGE:
                    r(3.5f, 5f, 20.5f, 19f, 2f);
                    poly(3.5f, 16.5f, 9f, 11f, 13f, 15f, 16f, 12.5f, 20.5f, 16.5f);
                    dot(15.5f, 9f, 1.4f);
                    break;
                case SEARCH:
                    c(10.5f, 10.5f, 6f);
                    l(15f, 15f, 20f, 20f);
                    break;
                case HOME:
                    poly(3.5f, 11.5f, 12f, 4f, 20.5f, 11.5f);
                    poly(6f, 10f, 6f, 20f, 18f, 20f, 18f, 10f);
                    poly(10f, 20f, 10f, 14.5f, 14f, 14.5f, 14f, 20f);
                    break;
                case PIN:
                    closed(9f, 4f, 15f, 4f, 14.5f, 10f, 17.5f, 13.5f, 6.5f, 13.5f, 9.5f, 10f);
                    l(12f, 13.5f, 12f, 20.5f);
                    break;
                case ARCHIVE:
                    r(3.5f, 4.5f, 20.5f, 9f, 1.2f);
                    poly(5f, 9f, 5f, 19.5f, 19f, 19.5f, 19f, 9f);
                    l(9.5f, 13f, 14.5f, 13f);
                    break;
                case SHARE:
                    c(6.5f, 12f, 2.3f);
                    c(17f, 6f, 2.3f);
                    c(17f, 18f, 2.3f);
                    l(8.6f, 10.9f, 14.9f, 7.1f);
                    l(8.6f, 13.1f, 14.9f, 16.9f);
                    break;
                case STORE:
                    closed(5f, 8f, 19f, 8f, 20f, 20f, 4f, 20f);
                    arc(8.5f, 3.5f, 15.5f, 11.5f, 180f, 180f);
                    break;
                case SORT:
                    l(4f, 7f, 20f, 7f);
                    l(4f, 12f, 15f, 12f);
                    l(4f, 17f, 10f, 17f);
                    break;
                case SELECT:
                    r(4f, 4f, 20f, 20f, 2.5f);
                    poly(8f, 12.5f, 11f, 15.5f, 16.5f, 9f);
                    break;
                case APP:
                    r(4.5f, 4.5f, 19.5f, 19.5f, 4.5f);
                    dot(12f, 12f, 2.4f);
                    break;
                case GEAR:
                    c(12f, 12f, 3.2f);
                    c(12f, 12f, 7f);
                    l(12f, 2.8f, 12f, 5f);
                    l(12f, 19f, 12f, 21.2f);
                    l(2.8f, 12f, 5f, 12f);
                    l(19f, 12f, 21.2f, 12f);
                    l(5.5f, 5.5f, 7f, 7f);
                    l(17f, 17f, 18.5f, 18.5f);
                    l(18.5f, 5.5f, 17f, 7f);
                    l(7f, 17f, 5.5f, 18.5f);
                    break;
                case HELP:
                    c(12f, 12f, 8.5f);
                    arc(9f, 6.8f, 15f, 12.4f, 180f, 250f);
                    l(12f, 12.6f, 12f, 14f);
                    dot(12f, 16.8f, 1.2f);
                    break;
                case FRAME:
                    r(4f, 4f, 20f, 20f, 3.5f);
                    break;
                case STOP:
                    c(12f, 12f, 8.5f);
                    l(9f, 9f, 15f, 15f);
                    l(15f, 9f, 9f, 15f);
                    break;
                case DESKTOP:
                    r(3.5f, 5f, 20.5f, 16.5f, 2f);
                    l(8.5f, 20f, 15.5f, 20f);
                    l(12f, 16.5f, 12f, 20f);
                    break;
                case CHEVRON:
                    poly(9f, 6f, 15f, 12f, 9f, 18f);
                    break;
                case PERSON:
                    c(12f, 8f, 3.6f);
                    arc(5f, 13.5f, 19f, 27f, 180f, 180f);
                    break;
                case GROUP:
                    c(9f, 8.5f, 3.2f);
                    arc(3f, 13.5f, 15f, 26f, 180f, 180f);
                    c(17f, 9.5f, 2.5f);
                    arc(13.5f, 14.5f, 21.5f, 24f, 200f, 160f);
                    break;
                case PHONE:
                    poly(6f, 4f, 9.5f, 4f, 11f, 8.5f, 9f, 10f, 14f, 15f, 15.5f, 13f, 20f, 14.5f, 20f, 18f);
                    arc(4f, 2f, 22f, 20f, 60f, 130f);
                    break;
                case BUBBLE:
                    r(3.5f, 4.5f, 20.5f, 16.5f, 3f);
                    poly(8f, 16.5f, 8f, 20.5f, 12.5f, 16.5f);
                    break;
                case BELL:
                    arc(6f, 4f, 18f, 16f, 180f, 180f);
                    poly(6f, 10f, 6f, 15f, 4.5f, 17.5f, 19.5f, 17.5f, 18f, 15f, 18f, 10f);
                    arc(10f, 17f, 14f, 21f, 0f, 180f);
                    break;
                case MUTE:
                    closed(4f, 9.5f, 8f, 9.5f, 12.5f, 5.5f, 12.5f, 18.5f, 8f, 14.5f, 4f, 14.5f);
                    l(15.5f, 9.5f, 20.5f, 14.5f);
                    l(20.5f, 9.5f, 15.5f, 14.5f);
                    break;
                case CLOCK:
                    c(12f, 12f, 8.5f);
                    poly(12f, 7f, 12f, 12f, 15.5f, 14f);
                    break;
                case QR:
                    r(4f, 4f, 10f, 10f, 1f);
                    r(14f, 4f, 20f, 10f, 1f);
                    r(4f, 14f, 10f, 20f, 1f);
                    dot(15f, 15f, 1.2f);
                    dot(19f, 15f, 1.2f);
                    dot(15f, 19f, 1.2f);
                    dot(19f, 19f, 1.2f);
                    break;
                case CAMERA:
                    r(3.5f, 7f, 20.5f, 19f, 2.5f);
                    poly(8.5f, 7f, 10f, 4.5f, 14f, 4.5f, 15.5f, 7f);
                    c(12f, 13f, 3.2f);
                    break;
                case MIC:
                    r(9f, 3.5f, 15f, 14f, 3f);
                    arc(6f, 7f, 18f, 17.5f, 0f, 180f);
                    l(12f, 17.5f, 12f, 20.5f);
                    break;
                case ATTACH:
                    poly(16.5f, 7f, 16.5f, 16f);
                    arc(7.5f, 11.5f, 16.5f, 20.5f, 0f, 180f);
                    poly(7.5f, 16f, 7.5f, 6.5f);
                    arc(7.5f, 3.5f, 13.5f, 9.5f, 180f, 180f);
                    l(13.5f, 6.5f, 13.5f, 15.5f);
                    break;
                case REPLY:
                    poly(10f, 6f, 4.5f, 11.5f, 10f, 17f);
                    poly(4.5f, 11.5f, 14f, 11.5f, 19.5f, 15f, 19.5f, 19f);
                    break;
                case FORWARD:
                    poly(14f, 6f, 19.5f, 11.5f, 14f, 17f);
                    poly(19.5f, 11.5f, 10f, 11.5f, 4.5f, 15f, 4.5f, 19f);
                    break;
                case SAVE:
                    l(12f, 4f, 12f, 15f);
                    poly(7.5f, 10.5f, 12f, 15f, 16.5f, 10.5f);
                    poly(4.5f, 16f, 4.5f, 20f, 19.5f, 20f, 19.5f, 16f);
                    break;
                case VIDEO:
                    r(3.5f, 6.5f, 15.5f, 17.5f, 2f);
                    closed(15.5f, 10.5f, 20.5f, 7.5f, 20.5f, 16.5f, 15.5f, 13.5f);
                    break;
                case MAIL:
                    r(3.5f, 5.5f, 20.5f, 18.5f, 2f);
                    poly(4f, 7f, 12f, 13f, 20f, 7f);
                    break;
                case WARNING:
                    closed(12f, 4f, 21f, 19.5f, 3f, 19.5f);
                    l(12f, 10f, 12f, 14.5f);
                    dot(12f, 17f, 1.1f);
                    break;
                case FLASH:
                    closed(13f, 3.5f, 6f, 13.5f, 11.5f, 13.5f, 10.5f, 20.5f, 18f, 10f, 12.5f, 10f);
                    break;
                case TICK2:
                    poly(2.5f, 12.5f, 6.5f, 16.5f, 14.5f, 8f);
                    poly(10f, 15.5f, 11f, 16.5f, 19f, 8f);
                    break;
                case LINK:
                    arc(3.5f, 8f, 12.5f, 16f, 60f, 240f);
                    arc(11.5f, 8f, 20.5f, 16f, 240f, 240f);
                    l(9f, 12f, 15f, 12f);
                    break;
            }
        }
    }
}
