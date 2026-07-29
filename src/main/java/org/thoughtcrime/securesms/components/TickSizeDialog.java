package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * shiroikuma fork (Step 11): size picker for the delivery ticks. A slider plus a typed number,
 * with a live preview that renders the currently configured "Sent" and "Received" glyphs at the
 * chosen size so the effect is visible before committing. Built in code - it is one row of
 * controls and needs no layout file.
 */
public class TickSizeDialog {

  public interface OnSizeSelectedListener {
    void onSizeSelected(int sizeDp);
  }

  public static void show(
      Context context, String title, int initialDp, OnSizeSelectedListener listener) {
    final int[] current = {clamp(initialDp)};
    final boolean[] updating = {false};

    int pad = ViewUtil.dpToPx(context, 20);
    LinearLayout root = new LinearLayout(context);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(pad, pad, pad, pad);

    LinearLayout previewRow = new LinearLayout(context);
    previewRow.setOrientation(LinearLayout.HORIZONTAL);
    previewRow.setGravity(Gravity.CENTER);
    previewRow.setPadding(0, 0, 0, ViewUtil.dpToPx(context, 16));

    final ImageView sentPreview = new ImageView(context);
    final ImageView receivedPreview = new ImageView(context);
    for (ImageView iv : new ImageView[] {sentPreview, receivedPreview}) {
      iv.setAdjustViewBounds(true);
      iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, ViewUtil.dpToPx(context, current[0]));
      lp.setMarginEnd(ViewUtil.dpToPx(context, 12));
      iv.setLayoutParams(lp);
      previewRow.addView(iv);
    }
    sentPreview.setImageResource(
        DeliveryStatusView.glyphRes(Prefs.getTickGlyph(context, Prefs.TICK_SENT)));
    sentPreview.setColorFilter(Prefs.getTickColor(context, Prefs.TICK_SENT));
    receivedPreview.setImageResource(
        DeliveryStatusView.glyphRes(Prefs.getTickGlyph(context, Prefs.TICK_RECEIVED)));
    receivedPreview.setColorFilter(Prefs.getTickColor(context, Prefs.TICK_RECEIVED));
    root.addView(previewRow);

    LinearLayout sliderRow = new LinearLayout(context);
    sliderRow.setOrientation(LinearLayout.HORIZONTAL);
    sliderRow.setGravity(Gravity.CENTER_VERTICAL);

    final SeekBar seek = new SeekBar(context);
    seek.setMax(Prefs.TICK_SIZE_MAX - Prefs.TICK_SIZE_MIN);
    seek.setProgress(current[0] - Prefs.TICK_SIZE_MIN);
    seek.setLayoutParams(
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    sliderRow.addView(seek);

    final EditText number = new EditText(context);
    number.setInputType(InputType.TYPE_CLASS_NUMBER);
    number.setText(String.valueOf(current[0]));
    number.setWidth(ViewUtil.dpToPx(context, 64));
    number.setGravity(Gravity.CENTER);
    sliderRow.addView(number);

    TextView unit = new TextView(context);
    unit.setText("dp");
    unit.setPadding(ViewUtil.dpToPx(context, 6), 0, 0, 0);
    sliderRow.addView(unit);

    root.addView(sliderRow);

    final Runnable applyPreview =
        () -> {
          int px = ViewUtil.dpToPx(context, current[0]);
          for (ImageView iv : new ImageView[] {sentPreview, receivedPreview}) {
            ViewGroup.LayoutParams lp = iv.getLayoutParams();
            lp.height = px;
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            iv.setLayoutParams(lp);
          }
        };

    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) return;
            current[0] = clamp(progress + Prefs.TICK_SIZE_MIN);
            applyPreview.run();
            if (!updating[0]) {
              updating[0] = true;
              number.setText(String.valueOf(current[0]));
              number.setSelection(number.getText().length());
              updating[0] = false;
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    number.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (updating[0]) return;
            try {
              current[0] = clamp(Integer.parseInt(s.toString().trim()));
            } catch (NumberFormatException e) {
              return;
            }
            applyPreview.run();
            updating[0] = true;
            seek.setProgress(current[0] - Prefs.TICK_SIZE_MIN);
            updating[0] = false;
          }
        });

    new AlertDialog.Builder(context)
        .setTitle(title)
        .setView(root)
        .setPositiveButton(
            android.R.string.ok, (d, w) -> listener.onSizeSelected(clamp(current[0])))
        .setNeutralButton(
            R.string.reset, (d, w) -> listener.onSizeSelected(Prefs.TICK_SIZE_DEFAULT))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private static int clamp(int dp) {
    if (dp < Prefs.TICK_SIZE_MIN) return Prefs.TICK_SIZE_MIN;
    if (dp > Prefs.TICK_SIZE_MAX) return Prefs.TICK_SIZE_MAX;
    return dp;
  }
}
