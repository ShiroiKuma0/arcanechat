package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.FontUtil;

/**
 * shiroikuma fork (Step 3): font picker (family + weight + size) with a live preview. The family
 * list includes user-imported fonts. The size SeekBar covers 0..SIZE_MAX (0 = Default), and the
 * editable size box accepts any value up to SIZE_HARD_CAP - larger than the slider's range.
 */
public class FontPickerDialog {

  public interface OnFontSelectedListener {
    void onFontSelected(String family, int weight, int size);
  }

  public static void show(
      Context context,
      String title,
      String initialFamily,
      int initialWeight,
      int initialSize,
      OnFontSelectedListener listener) {

    final String[] familyValues = FontUtil.allFamilyValues(context);
    final String[] familyLabels = FontUtil.allFamilyLabels(context);

    View view = LayoutInflater.from(context).inflate(R.layout.dialog_font_picker, null);
    final TextView preview = view.findViewById(R.id.font_preview);
    final Spinner familySpinner = view.findViewById(R.id.font_family);
    final Spinner weightSpinner = view.findViewById(R.id.font_weight);
    final SeekBar sizeBar = view.findViewById(R.id.font_size);
    final EditText sizeValue = view.findViewById(R.id.font_size_value);

    familySpinner.setAdapter(
        new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, familyLabels));
    weightSpinner.setAdapter(
        new ArrayAdapter<>(
            context, android.R.layout.simple_spinner_dropdown_item, FontUtil.WEIGHT_LABELS));

    final int[] sel = {
      FontUtil.indexOf(familyValues, initialFamily), FontUtil.weightIndex(initialWeight), initialSize
    };
    final boolean[] updating = {false};

    familySpinner.setSelection(sel[0]);
    weightSpinner.setSelection(sel[1]);
    sizeBar.setMax(FontUtil.SIZE_MAX);
    sizeBar.setProgress(Math.min(Math.max(initialSize, 0), FontUtil.SIZE_MAX));

    final Runnable refresh =
        () -> {
          String family = familyValues[sel[0]];
          int weight = FontUtil.WEIGHT_VALUES[sel[1]];
          int size = sel[2];
          preview.setTypeface(null);
          preview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
          FontUtil.apply(preview, family, weight, size > 0 ? size : 0);
        };

    final Runnable showSizeInBox =
        () -> {
          updating[0] = true;
          sizeValue.setText(sel[2] <= 0 ? "" : String.valueOf(sel[2]));
          updating[0] = false;
        };
    showSizeInBox.run();

    familySpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            sel[0] = pos;
            refresh.run();
          }

          @Override
          public void onNothingSelected(AdapterView<?> p) {}
        });
    weightSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            sel[1] = pos;
            refresh.run();
          }

          @Override
          public void onNothingSelected(AdapterView<?> p) {}
        });

    sizeBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
            if (!fromUser) return;
            sel[2] = progress;
            showSizeInBox.run();
            refresh.run();
          }

          @Override
          public void onStartTrackingTouch(SeekBar s) {}

          @Override
          public void onStopTrackingTouch(SeekBar s) {}
        });

    sizeValue.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void onTextChanged(CharSequence s, int a, int b, int c) {}

          @Override
          public void afterTextChanged(Editable e) {
            if (updating[0]) return;
            String t = e.toString().trim();
            int v;
            if (t.isEmpty()) {
              v = 0;
            } else {
              try {
                v = Integer.parseInt(t);
              } catch (NumberFormatException ex) {
                return;
              }
              if (v > FontUtil.SIZE_HARD_CAP) {
                v = FontUtil.SIZE_HARD_CAP;
                updating[0] = true;
                sizeValue.setText(String.valueOf(v));
                sizeValue.setSelection(sizeValue.getText().length());
                updating[0] = false;
              }
            }
            sel[2] = v;
            sizeBar.setProgress(Math.min(v, FontUtil.SIZE_MAX)); // slider pins at its max
            refresh.run();
          }
        });

    refresh.run();

    new AlertDialog.Builder(context)
        .setTitle(title)
        .setView(view)
        .setPositiveButton(
            android.R.string.ok,
            (d, w) ->
                listener.onFontSelected(familyValues[sel[0]], FontUtil.WEIGHT_VALUES[sel[1]], sel[2]))
        .setNeutralButton("Reset", (d, w) -> listener.onFontSelected("", 0, 0))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }
}
