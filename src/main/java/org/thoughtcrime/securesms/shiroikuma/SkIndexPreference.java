package org.thoughtcrime.securesms.shiroikuma;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.R;

/**
 * shiroikuma fork: the chip index of the UI page - a wrapping grid of small outlined chips, one
 * for every section, each as wide as its icon and label. A tap unfolds the section and scrolls to
 * it. A long-press lifts the chip into a drag: the chips reorder live under the finger, and the
 * sections below follow on drop.
 */
public class SkIndexPreference extends Preference {

  /** What the page does when a chip is tapped or the order changes. */
  public interface Host {
    @NonNull
    List<SkSectionCategory> sections();

    void jump(@NonNull SkSectionCategory section);

    void reorder(@NonNull List<String> order);
  }

  private final Host host;
  private SkFlowLayout flow;
  private View dragged;
  private int lastTarget = -1;

  public SkIndexPreference(@NonNull Context context, @NonNull Host host) {
    super(context);
    this.host = host;
    setLayoutResource(R.layout.sk_pref_index);
    setKey("sk_index");
    setPersistent(false);
    setSelectable(false);
  }

  @Override
  public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    holder.setDividerAllowedAbove(false);
    holder.setDividerAllowedBelow(false);
    // The row's root itself: AndroidX replaces the id of every row's root view when it binds
    // the row, so the root can never be looked up by the id its layout gave it.
    if (!(holder.itemView instanceof FrameLayout)) return;
    FrameLayout frame = (FrameLayout) holder.itemView;
    frame.removeAllViews();
    Context c = getContext();
    flow = new SkFlowLayout(c, SkPills.dp(c, 6));
    int frameColor = SkRoles.get(c, SkRole.CHIP_FRAME);
    int labelColor = SkRoles.get(c, SkRole.CHIP_LABEL);
    for (final SkSectionCategory section : host.sections()) {
      final TextView chip = new TextView(c);
      chip.setText(section.getTitle());
      chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      chip.setTextColor(labelColor);
      chip.setSingleLine(true);
      chip.setBackground(SkPills.frame(c, SkPills.REGULAR_DP, frameColor, Color.TRANSPARENT));
      chip.setPaddingRelative(SkPills.dp(c, 9), SkPills.dp(c, 5), SkPills.dp(c, 11), SkPills.dp(c, 5));
      // The same icon in the same colour as the section's heading.
      SkIcons.onLabel(chip, section.icon(), SkRoles.get(c, section.color()), false, 0);
      chip.setCompoundDrawablePadding(SkPills.dp(c, 5));
      chip.setTag(section.id());
      chip.setOnClickListener(v -> host.jump(section));
      chip.setOnLongClickListener(
          v -> {
            dragged = v;
            lastTarget = -1;
            v.setAlpha(0.3f);
            if (android.os.Build.VERSION.SDK_INT >= 24) {
              v.startDragAndDrop(null, new View.DragShadowBuilder(v), v, 0);
            } else {
              v.startDrag(null, new View.DragShadowBuilder(v), v, 0);
            }
            return true;
          });
      flow.addView(
          chip,
          new ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    flow.setOnDragListener((v, event) -> onDrag(event));
    frame.addView(
        flow,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  /** Draw the chips again, in the order the page now has. */
  public void redraw() {
    notifyChanged();
  }

  private boolean onDrag(@NonNull DragEvent event) {
    if (dragged == null || flow == null) return false;
    switch (event.getAction()) {
      case DragEvent.ACTION_DRAG_LOCATION:
        {
          // Live: the chip moves to the slot under the finger. The drag itself is carried by the
          // shadow, so moving the view in the tree does not end it.
          int target = flow.childAt(event.getX(), event.getY());
          int from = flow.indexOfChild(dragged);
          if (target < 0 || target == from) {
            lastTarget = -1;
            return true;
          }
          // Chips differ in width, so a swap can put the neighbour back under the finger:
          // without this guard the two would flap on every event.
          if (target == lastTarget) return true;
          flow.removeView(dragged);
          flow.addView(dragged, target);
          lastTarget = from;
          return true;
        }
      case DragEvent.ACTION_DRAG_ENDED:
        {
          dragged.setAlpha(1f);
          dragged = null;
          List<String> order = new ArrayList<>();
          for (int i = 0; i < flow.getChildCount(); i++) {
            order.add((String) flow.getChildAt(i).getTag());
          }
          // The sections follow; their fold state is untouched.
          host.reorder(order);
          return true;
        }
      default:
        return true;
    }
  }
}
