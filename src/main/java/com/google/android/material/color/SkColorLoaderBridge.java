package com.google.android.material.color;

import android.content.Context;
import android.content.res.loader.ResourcesLoader;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.util.Map;

/**
 * shiroikuma fork: one class in Material's own package, because the table writer that turns a map
 * of colour resource id to colour into a {@link ResourcesLoader} is package-private there. Used
 * only by {@code shiroikuma/SkPalette}. When Material is upgraded, check that {@code
 * ColorResourcesLoaderCreator.create(Context, Map)} still exists.
 */
public final class SkColorLoaderBridge {
  private SkColorLoaderBridge() {}

  /** A loader that replaces the given colour resources, or null where that cannot be done. */
  @Nullable
  @RequiresApi(Build.VERSION_CODES.R)
  public static ResourcesLoader create(
      @NonNull Context context, @NonNull Map<Integer, Integer> colors) {
    try {
      return ColorResourcesLoaderCreator.create(context, colors);
    } catch (Throwable t) {
      return null;
    }
  }
}
