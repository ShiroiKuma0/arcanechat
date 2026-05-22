package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;
import org.thoughtcrime.securesms.util.FontUtil;
import org.thoughtcrime.securesms.util.Prefs;

public abstract class CorrectedPreferenceFragment extends PreferenceFragmentCompat {
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    View lv = getView().findViewById(android.R.id.list);
    if (lv != null) lv.setPadding(0, 0, 0, 0);
  }

  // shiroikuma fork (Step 3): configurable settings font, applied to every TextView in the
  // preference list. RecyclerView recycles rows, so we apply on child-attach as well as to the
  // rows already on screen. No-op unless a settings font is actually configured.
  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    Context ctx = getContext();
    if (ctx == null) return;
    final String family = Prefs.getFontFamily(ctx, Prefs.FONT_SETTINGS);
    final int weight = Prefs.getFontWeight(ctx, Prefs.FONT_SETTINGS);
    final int size = Prefs.getFontSize(ctx, Prefs.FONT_SETTINGS);
    if ((family == null || family.isEmpty()) && weight == 0 && size == 0) return;
    final RecyclerView rv = getListView();
    if (rv == null) return;
    rv.addOnChildAttachStateChangeListener(
        new RecyclerView.OnChildAttachStateChangeListener() {
          @Override
          public void onChildViewAttachedToWindow(@NonNull View v) {
            applyFontToTree(v, family, weight, size);
          }

          @Override
          public void onChildViewDetachedFromWindow(@NonNull View v) {}
        });
    rv.post(
        () -> {
          for (int i = 0; i < rv.getChildCount(); i++) {
            applyFontToTree(rv.getChildAt(i), family, weight, size);
          }
        });
  }

  private void applyFontToTree(View v, String family, int weight, int size) {
    if (v instanceof TextView) {
      FontUtil.apply((TextView) v, family, weight, size);
    } else if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        applyFontToTree(g.getChildAt(i), family, weight, size);
      }
    }
  }
}
