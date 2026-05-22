package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import androidx.appcompat.app.AlertDialog;
import org.thoughtcrime.securesms.R;

/**
 * shiroikuma fork (Step 2): a small reusable RGB + hex colour picker. Opaque colours only (the
 * configurable roles don't need alpha). Returns the chosen ARGB int via the callback.
 */
public class ColorPickerDialog {

  public interface OnColorSelectedListener {
    void onColorSelected(int color);
  }

  public static void show(
      Context context, String title, int initialColor, OnColorSelectedListener listener) {
    View view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null);

    final View preview = view.findViewById(R.id.color_preview);
    final EditText hex = view.findViewById(R.id.color_hex);
    final SeekBar red = view.findViewById(R.id.color_red);
    final SeekBar green = view.findViewById(R.id.color_green);
    final SeekBar blue = view.findViewById(R.id.color_blue);

    final int[] current = {initialColor | 0xFF000000};
    // guard against the seekbar<->hex listeners re-triggering each other
    final boolean[] updating = {false};

    final Runnable refreshPreview =
        () -> preview.setBackgroundColor(current[0]);

    final Runnable syncFromRgb =
        () -> {
          current[0] =
              Color.rgb(red.getProgress(), green.getProgress(), blue.getProgress());
          refreshPreview.run();
          if (!updating[0]) {
            updating[0] = true;
            hex.setText(String.format("#%06X", current[0] & 0xFFFFFF));
            hex.setSelection(hex.getText().length());
            updating[0] = false;
          }
        };

    SeekBar.OnSeekBarChangeListener seekListener =
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) syncFromRgb.run();
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    red.setOnSeekBarChangeListener(seekListener);
    green.setOnSeekBarChangeListener(seekListener);
    blue.setOnSeekBarChangeListener(seekListener);

    hex.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void onTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (updating[0]) return;
            String t = s.toString().trim();
            if (!t.startsWith("#")) t = "#" + t;
            if (t.length() != 7) return;
            try {
              int parsed = Color.parseColor(t) | 0xFF000000;
              current[0] = parsed;
              updating[0] = true;
              red.setProgress(Color.red(parsed));
              green.setProgress(Color.green(parsed));
              blue.setProgress(Color.blue(parsed));
              updating[0] = false;
              refreshPreview.run();
            } catch (IllegalArgumentException ignored) {
            }
          }
        });

    // initialise widgets from the starting colour
    red.setProgress(Color.red(current[0]));
    green.setProgress(Color.green(current[0]));
    blue.setProgress(Color.blue(current[0]));
    hex.setText(String.format("#%06X", current[0] & 0xFFFFFF));
    refreshPreview.run();

    new AlertDialog.Builder(context)
        .setTitle(title)
        .setView(view)
        .setPositiveButton(
            android.R.string.ok, (dialog, which) -> listener.onColorSelected(current[0]))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }
}
