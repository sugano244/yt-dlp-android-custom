package jp.nx809j.sdrlab;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * Read-only, non-privileged checks. This does NOT capture RF and is unable to
 * exclude hidden/undocumented hardware. No identifiers or user data collected.
 */
public final class RfReadiness {
    private RfReadiness() {}

    private static void line(StringBuilder out, String label, Object value) {
        out.append(label).append(": ").append(value).append('\n');
    }

    private static boolean has(Context context, String feature) {
        try { return context.getPackageManager().hasSystemFeature(feature); }
        catch (RuntimeException error) { return false; }
    }

    private static String describePath(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return "not visible";
            String[] entries = file.isDirectory() ? file.list() : null;
            if (file.isDirectory()) {
                if (entries == null) return "exists; directory not readable";
                return "exists; entries=" + entries.length;
            }
            return "exists; readable=" + file.canRead();
        } catch (SecurityException error) {
            return "access denied";
        }
    }

    private static void inspectDev(StringBuilder out) {
        String[] roots = {"/dev", "/dev/dvb"};
        for (String root : roots) {
            File folder = new File(root);
            try {
                String[] entries = folder.list();
                if (entries == null) { line(out, root, "not accessible or not a directory"); continue; }
                int count = 0;
                for (String entry : entries) {
                    String lower = entry.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("dvb") || lower.startsWith("radio")
                        || lower.contains("tuner") || lower.contains("isdb")
                        || lower.contains("rtl283") || lower.contains("sdr")) {
                        out.append("  ").append(root).append("/").append(entry).append('\n');
                        count++;
                    }
                }
                if (count == 0) out.append("  ").append(root).append(": no matching entries visible\n");
            } catch (SecurityException error) {
                line(out, root, "access denied");
            }
        }
    }

    private static void inspectManifests(StringBuilder out) {
        String[] possible = {
            "/vendor/etc/vintf/manifest.xml",
            "/odm/etc/vintf/manifest.xml",
            "/system/etc/vintf/manifest.xml"
        };
        for (String path : possible) {
            File file = new File(path);
            if (!file.exists()) { line(out, path, "not visible"); continue; }
            int lines = 0; boolean mention = false;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String row;
                while (lines++ < 30000 && (row = reader.readLine()) != null) {
                    if (row.contains("android.hardware.tv.tuner")
                        || row.contains("android.hardware.tv.input")
                        || row.contains("android.hardware.broadcastradio")) {
                        mention = true;
                    }
                }
                line(out, path, mention ? "contains tuner/input/radio HAL mention (not proof of ISDB-T)" :
                    "readable; no TV-tuner mention (fragment manifests not checked)");
            } catch (Exception error) {
                line(out, path, "not readable ("+error.getClass().getSimpleName()+")");
            }
        }
    }

    public static String inspect(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("=== NX809J RF FEASIBILITY / v0.3 ===\n");
        out.append("READ-ONLY. NO REAL RF RECEPTION OR TV CHANNEL SCAN.\n");
        line(out, "Android", Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT);
        line(out, "Model", Build.MODEL);
        line(out, "Chip hardware ID", Build.HARDWARE);
        line(out, "Board", Build.BOARD);
        if (Build.VERSION.SDK_INT >= 31) line(out, "SoC", Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL);

        PackageManager pm = context.getPackageManager();
        boolean feature = has(context, "android.hardware.tv.tuner");
        line(out, "android.hardware.tv.tuner", feature ? "YES" : "NO");
        line(out, "android.software.live_tv", has(context,"android.software.live_tv") ? "YES" : "NO");
        line(out, "android.hardware.telephony", has(context, PackageManager.FEATURE_TELEPHONY) ? "YES (cellular only, not TV evidence)" : "NO");

        int permission = context.checkSelfPermission("android.permission.ACCESS_TV_TUNER");
        line(out, "ACCESS_TV_TUNER", permission == PackageManager.PERMISSION_GRANTED ?
            "GRANTED (privileged-only on standard Android)" : "DENIED (privileged permission; not requestable via dialog)");

        try {
            Object service = context.getSystemService("tv_tuner_resource_mgr");
            line(out, "TV tuner resource service", service == null ? "not available to app" :
                "visible to app (does NOT prove tuner hardware)");
        } catch (Exception error) {
            line(out, "TV tuner resource service", "not accessible ("+error.getClass().getSimpleName()+")");
        }

        out.append("\nDevice node hints (not exhaustive):\n");
        inspectDev(out);
        line(out, "/sys/class/dvb", describePath("/sys/class/dvb"));
        line(out, "/sys/class/video4linux", describePath("/sys/class/video4linux") + " (may be camera; NOT TV evidence)");

        out.append("\nVINTF manifest hints (partial):\n");
        inspectManifests(out);

        out.append("\n=== EVIDENCE-BASED VERDICT ===\n");
        if (!feature) {
            out.append("No declared TV-tuner hardware feature. Standard APK has no confirmed TV RF input.\n");
        } else {
            out.append("TV-tuner feature is advertised; hardware type / ISDB-T capability NOT yet proven.\n");
        }
        if (permission != PackageManager.PERMISSION_GRANTED) {
            out.append("Standard third-party APK does not have privileged ACCESS_TV_TUNER.\n");
        }
        out.append("This test cannot verify or rule out physically hidden RF circuits or modem diagnostic modes.\n");
        out.append("A simulated tuner/FFT cannot turn cellular antennas into an accessible ISDB-T IQ source.\n");
        out.append("Live over-the-air TV needs a compatible physical RF capture path and permitted access.\n");
        out.append("No root, radio writes, spectrum transmissions, tracking, serial/IMEI, or network calls.\n");
        return out.toString();
    }
}
