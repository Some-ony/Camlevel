package rac.camlevel;

import android.hardware.camera2.CameraCharacteristics;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.TreeSet;

/**
 * Camera2 characteristics spoof, applied ONLY inside the target GCam package.
 *  - INFO_SUPPORTED_HARDWARE_LEVEL: LIMITED(0) -> FULL(1). Never downgrades, never touches other levels.
 *  - REQUEST_AVAILABLE_CAPABILITIES: adds MANUAL_SENSOR(1), MANUAL_POST_PROCESSING(2), RAW(3).
 * Mode (best effort, via system property debug.camlevel.mode): off | level | full (default full).
 * Primary kill switch = disable the module in LSPosed.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.samsung.android.ruler";
    private static final String K_LEVEL = "android.info.supportedHardwareLevel";
    private static final String K_CAPS = "android.request.availableCapabilities";
    private static final int[] EXTRA_CAPS = {1, 2, 3};
    private static volatile boolean loggedLevel, loggedCaps;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET.equals(lpparam.packageName)) return;

        XC_MethodHook cb = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try { apply(p); } catch (Throwable t) { XposedBridge.log("CamLevel: apply failed: " + t); }
            }
        };

        // 1) public API read path (covers Java-side reads)
        try {
            XposedHelpers.findAndHookMethod(CameraCharacteristics.class, "get", CameraCharacteristics.Key.class, cb);
            XposedBridge.log("CamLevel: hooked CameraCharacteristics.get in " + TARGET);
        } catch (Throwable t) { XposedBridge.log("CamLevel: hook#1 failed: " + t); }

        // 2) lower level (hidden class); best effort, idempotent with hook #1
        try {
            Class<?> nat = XposedHelpers.findClassIfExists(
                    "android.hardware.camera2.impl.CameraMetadataNative", lpparam.classLoader);
            if (nat != null) {
                XposedBridge.hookAllMethods(nat, "get", cb);
                XposedBridge.log("CamLevel: hooked CameraMetadataNative.get");
            }
        } catch (Throwable t) { XposedBridge.log("CamLevel: hook#2 failed: " + t); }
    }

    private static void apply(XC_MethodHook.MethodHookParam p) throws Throwable {
        if (p.args == null || p.args.length != 1 || p.args[0] == null) return;
        String name = keyName(p.args[0]);
        if (name == null) return;
        boolean isLevel = K_LEVEL.equals(name), isCaps = K_CAPS.equals(name);
        if (!isLevel && !isCaps) return;

        String mode = mode();
        if ("off".equals(mode)) return;

        Object r = p.getResult();
        if (isLevel) {
            if (r instanceof Integer && ((Integer) r) == 0) {          // LIMITED only
                p.setResult(Integer.valueOf(1));                        // FULL
                if (!loggedLevel) { loggedLevel = true; XposedBridge.log("CamLevel: level LIMITED -> FULL (mode=" + mode + ")"); }
            }
        } else if (!"level".equals(mode) && r instanceof int[]) {
            int[] cur = (int[]) r;
            TreeSet<Integer> s = new TreeSet<>();
            for (int c : cur) s.add(c);
            if (!s.contains(0)) return;                                 // only if BACKWARD_COMPATIBLE present
            int before = s.size();
            for (int c : EXTRA_CAPS) s.add(c);
            if (s.size() == before) return;
            int[] out = new int[s.size()]; int i = 0;
            for (int c : s) out[i++] = c;
            p.setResult(out);
            if (!loggedCaps) { loggedCaps = true; XposedBridge.log("CamLevel: caps += MANUAL_SENSOR, MANUAL_POST_PROCESSING, RAW"); }
        }
    }

    private static String keyName(Object key) {
        try {
            Method m = key.getClass().getMethod("getName");
            Object n = m.invoke(key);
            return n instanceof String ? (String) n : null;
        } catch (Throwable t) { return null; }
    }

    private static String mode() {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method m = c.getMethod("get", String.class, String.class);
            String v = (String) m.invoke(null, "debug.camlevel.mode", "full");
            return v == null || v.isEmpty() ? "full" : v;
        } catch (Throwable t) { return "full"; }
    }
}
