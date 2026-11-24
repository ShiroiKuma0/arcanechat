package org.thoughtcrime.securesms.shiroikuma;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.color.SkColorLoaderBridge;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * shiroikuma fork: makes XML follow the colour roles. A theme attribute, a drawable or a layout
 * can only name a colour <em>resource</em>, and a resource is fixed at build time. From Android 11
 * a {@link ResourcesLoader} can replace resources at run time: every role is a resource ({@code
 * @color/sk_role_*}), the overlay {@code ThemeOverlay.Sk} points the theme's colour attributes at
 * them, and this class lays the stored colours over them - and over the upstream colour names that
 * upstream's layouts use directly ({@link SkRole#upstreamNames}), so upstream's XML is never
 * edited for colour. Below Android 11 XML shows the defaults; everything coloured in code follows
 * the roles on every version.
 *
 * <p>The loader's colour table carries the default configuration only, so a role colour resource
 * must live in the default {@code values/} and nowhere else.
 */
public final class SkPalette {
  private SkPalette() {}

  private static volatile int epoch = 1;
  private static int loaderEpoch = 0;
  @Nullable private static Object loader; // a ResourcesLoader; Object so the class loads below 30
  private static final WeakHashMap<Resources, Object> applied = new WeakHashMap<>();
  private static final WeakHashMap<Activity, Integer> activityEpoch = new WeakHashMap<>();

  /** Called once from the application: every activity gets the palette before it inflates. */
  public static void install(@NonNull Application app) {
    SkRoles.migrate(app);
    apply(app);
    app.registerActivityLifecycleCallbacks(
        new Application.ActivityLifecycleCallbacks() {
          @Override
          public void onActivityPreCreated(@NonNull Activity a, @Nullable Bundle b) {
            apply(a);
          }

          @Override
          public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {
            // Below Android 10 there is no pre-created callback; DynamicTheme calls apply().
          }

          @Override
          public void onActivityStarted(@NonNull Activity a) {}

          @Override
          public void onActivityResumed(@NonNull Activity a) {
            if (isStale(a)) a.recreate();
          }

          @Override
          public void onActivityPaused(@NonNull Activity a) {}

          @Override
          public void onActivityStopped(@NonNull Activity a) {}

          @Override
          public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}

          @Override
          public void onActivityDestroyed(@NonNull Activity a) {}
        });
  }

  /** A role changed: every screen is rebuilt with the new colours when it next comes forward. */
  public static void invalidate(@NonNull Context context) {
    epoch++;
    try {
      apply(context.getApplicationContext());
    } catch (Throwable ignore) {
    }
  }

  /** Lay the stored colours over this context's resources, before anything is inflated from it. */
  public static synchronized void apply(@NonNull Context context) {
    if (context instanceof Activity) activityEpoch.put((Activity) context, epoch);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
    try {
      Api30.apply(context);
    } catch (Throwable ignore) {
      // A platform that refuses the loader shows the defaults; code-drawn surfaces still follow.
    }
  }

  /** Whether this activity was built with colours that have changed since. */
  public static synchronized boolean isStale(@NonNull Activity activity) {
    Integer e = activityEpoch.get(activity);
    return e != null && e != epoch;
  }

  @NonNull
  static Map<Integer, Integer> colors(@NonNull Context context) {
    Map<Integer, Integer> map = new HashMap<>();
    Resources res = context.getResources();
    String pkg = context.getPackageName();
    for (SkRole r : SkRole.values()) {
      int value = SkRoles.get(context, r);
      map.put(r.colorRes, value);
      for (String name : r.upstreamNames) {
        // Looked up by name: an upstream colour that is renamed or removed is skipped, not a
        // build failure after a rebase.
        int id = res.getIdentifier(name, "color", pkg);
        if (id != 0) map.put(id, value);
      }
    }
    return map;
  }

  private static final class Api30 {
    static void apply(@NonNull Context context) {
      if (loader == null || loaderEpoch != epoch) {
        Context app =
            context.getApplicationContext() != null ? context.getApplicationContext() : context;
        loader = SkColorLoaderBridge.create(app, colors(app));
        loaderEpoch = epoch;
      }
      if (loader == null) return;
      Resources res = context.getResources();
      Object old = applied.get(res);
      if (old == loader) return;
      if (old != null) res.removeLoaders((ResourcesLoader) old);
      res.addLoaders((ResourcesLoader) loader);
      applied.put(res, loader);
    }
  }
}
