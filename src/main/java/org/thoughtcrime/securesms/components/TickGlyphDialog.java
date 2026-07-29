package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * shiroikuma fork (Step 11): glyph picker for one delivery state. Replaces the plain
 * {@code ListPreference} dialog, which listed bare labels in AppCompat's white
 * {@code textColorAlertDialogListItem} and gave no idea what the glyph actually looks like. Every
 * row here previews its glyph at the configured tick size, in that state's configured colour - the
 * same rendering path the chat footer uses - so the choice is made on the picture, not the name.
 */
public class TickGlyphDialog {

  public interface OnGlyphSelectedListener {
    void onGlyphSelected(String glyph);
  }

  public static void show(
      Context context, String title, String state, OnGlyphSelectedListener listener) {
    final String[] values = context.getResources().getStringArray(R.array.tick_glyph_values);
    final String[] labels = context.getResources().getStringArray(R.array.tick_glyph_entries);
    final String current = Prefs.getTickGlyph(context, state);
    final int colour = Prefs.getTickColor(context, state);
    final int sizeDp = Prefs.getTickSize(context);
    final int accent = resolveAccent(context);

    LinearLayout rows = new LinearLayout(context);
    rows.setOrientation(LinearLayout.VERTICAL);
    int padH = ViewUtil.dpToPx(context, 20);
    rows.setPadding(padH, ViewUtil.dpToPx(context, 8), padH, ViewUtil.dpToPx(context, 8));

    ScrollView scroller = new ScrollView(context);
    scroller.addView(rows);

    final AlertDialog dialog =
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setView(scroller)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

    for (int i = 0; i < values.length; i++) {
      final String value = values[i];

      LinearLayout row = new LinearLayout(context);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      int padV = ViewUtil.dpToPx(context, 6);
      row.setPadding(0, padV, 0, padV);
      row.setClickable(true);

      RadioButton radio = new RadioButton(context);
      radio.setText(labels[i]);
      radio.setTextColor(accent);
      radio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
      radio.setChecked(value.equals(current));
      radio.setClickable(false); // the whole row is the touch target
      radio.setFocusable(false);
      row.addView(radio);

      View spacer = new View(context);
      row.addView(
          spacer, new LinearLayout.LayoutParams(0, 1, 1f)); // pushes the preview to the right

      Drawable preview = DeliveryStatusView.glyphPreview(context, value, colour, sizeDp);
      if (preview != null) {
        ImageView icon = new ImageView(context);
        icon.setImageDrawable(preview);
        icon.setAdjustViewBounds(true);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(
            icon,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ViewUtil.dpToPx(context, sizeDp)));
      }

      row.setOnClickListener(
          v -> {
            listener.onGlyphSelected(value);
            dialog.dismiss();
          });
      rows.addView(
          row,
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    dialog.show();
  }

  private static int resolveAccent(Context context) {
    TypedValue tv = new TypedValue();
    if (context.getTheme().resolveAttribute(R.attr.colorAccent, tv, true)) {
      return tv.data != 0 ? tv.data : Prefs.COLOR_YELLOW;
    }
    return Prefs.COLOR_YELLOW;
  }
}
