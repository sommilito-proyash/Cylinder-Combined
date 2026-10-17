package com.cylinderradar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Persistent settings: pinned apps, hidden (vault) apps, theme, clock style and the vault password. */
final class Store {
    private final SharedPreferences sp;
    // The earlier one-file v0.2 build used a different preference namespace and SHA-256 PIN hash.
    private final SharedPreferences legacySp;

    Store(Context c) {
        sp = c.getSharedPreferences("cylinder_prefs", Context.MODE_PRIVATE);
        legacySp = c.getSharedPreferences("cylinder_radar_preferences", Context.MODE_PRIVATE);
        if (!sp.contains("accent") && legacySp.contains("radar_theme")) {
            sp.edit().putInt("accent", legacySp.getInt("radar_theme", 0)).apply();
        }
        if (!sp.contains("analog") && legacySp.contains("clock_mode")) {
            sp.edit().putBoolean("analog", legacySp.getInt("clock_mode", 0) == 1).apply();
        }
    }

    /** Convert v0.2's package-name based selections to this version's ComponentName keys. */
    void migrateLegacyAppLists(List<AppItem> apps) {
        if (sp.getBoolean("legacy_app_lists_migrated", false)) return;
        Set<String> oldHome = legacySp.getStringSet("home_app_packages", null);
        Set<String> oldHidden = legacySp.getStringSet("hidden_app_packages", null);
        if (!sp.contains("pinned") && oldHome != null) {
            ArrayList<String> pinned = new ArrayList<>();
            Set<String> mappedPackages = new HashSet<>();
            for (AppItem app : apps) {
                if (oldHome.contains(app.pkg) && mappedPackages.add(app.pkg)) pinned.add(app.key);
            }
            saveList("pinned", pinned);
            setInitDone(); // Preserve an intentionally empty Home selection too.
        }
        if (!sp.contains("hidden") && oldHidden != null) {
            ArrayList<String> hidden = new ArrayList<>();
            for (AppItem app : apps) if (oldHidden.contains(app.pkg)) hidden.add(app.key);
            saveList("hidden", hidden);
        }
        sp.edit().putBoolean("legacy_app_lists_migrated", true).apply();
    }

    // ---- lists -------------------------------------------------------------------------------
    List<String> list(String key) {
        ArrayList<String> r = new ArrayList<>();
        String s = sp.getString(key, "");
        if (s != null && !s.isEmpty()) {
            for (String x : s.split("\n")) if (!x.isEmpty()) r.add(x);
        }
        return r;
    }

    void saveList(String key, List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) {
            if (b.length() > 0) b.append('\n');
            b.append(s);
        }
        sp.edit().putString(key, b.toString()).apply();
    }

    void add(String key, String v) {
        List<String> l = list(key);
        if (!l.contains(v)) {
            l.add(v);
            saveList(key, l);
        }
    }

    void remove(String key, String v) {
        List<String> l = list(key);
        if (l.remove(v)) saveList(key, l);
    }

    boolean contains(String key, String v) { return list(key).contains(v); }

    // ---- appearance --------------------------------------------------------------------------
    int accent() { return sp.getInt("accent", 0); }
    void setAccent(int i) { sp.edit().putInt("accent", i).apply(); }
    boolean analog() { return sp.getBoolean("analog", false); }
    void setAnalog(boolean b) { sp.edit().putBoolean("analog", b).apply(); }
    boolean initDone() { return sp.getBoolean("init", false); }
    void setInitDone() { sp.edit().putBoolean("init", true).apply(); }

    // ---- vault password ----------------------------------------------------------------------
    boolean hasPassword() {
        if (!sp.getString("pw_hash", "").isEmpty()) return true;
        return !legacySp.getString("hidden_pin_hash", "").isEmpty()
                && !legacySp.getString("hidden_pin_salt", "").isEmpty();
    }

    /** "pin", "pattern" or "text" (old v1.0/1.1 password; verified through a masked field, then upgraded). */
    String credType() {
        if (sp.getString("pw_hash", "").isEmpty()) return "text";
        return sp.getString("cred_type", "text");
    }
    int pinLength() { return sp.getInt("pin_len", 0); }

    /** Stores a PIN ("1234") or pattern secret ("p:0147"). Only a salted PBKDF2 hash is kept. */
    void setCredential(String type, String secret) {
        setPassword(secret);
        sp.edit().putString("cred_type", type).putInt("pin_len", "pin".equals(type) ? secret.length() : 0).apply();
    }

    boolean randomKeypad() { return sp.getBoolean("rand_pad", false); }
    void setRandomKeypad(boolean b) { sp.edit().putBoolean("rand_pad", b).apply(); }
    boolean wallClock() { return sp.getBoolean("wall_clock", true); }
    void setWallClock(boolean b) { sp.edit().putBoolean("wall_clock", b).apply(); }
    /** 0 = airy (see-through), 1 = balanced, 2 = solid dark. */
    int dim() { return sp.getInt("dim", 1); }
    void setDim(int i) { sp.edit().putInt("dim", i).apply(); }

    void setPassword(String pw) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = Base64.encodeToString(salt, Base64.NO_WRAP);
        sp.edit().putString("pw_salt", s).putString("pw_hash", hash(s, pw)).putInt("fails", 0).putLong("lock_until", 0).apply();
    }

    boolean check(String pw) {
        String s = sp.getString("pw_salt", "");
        String h = sp.getString("pw_hash", "");
        if (!h.isEmpty() && !s.isEmpty()) {
            return MessageDigest.isEqual(h.getBytes(StandardCharsets.UTF_8), hash(s, pw).getBytes(StandardCharsets.UTF_8));
        }
        // If the phone had the v0.2 build installed, verify its old PIN once and upgrade it to PBKDF2.
        String oldSalt = legacySp.getString("hidden_pin_salt", "");
        String oldHash = legacySp.getString("hidden_pin_hash", "");
        if (oldSalt.isEmpty() || oldHash.isEmpty()) return false;
        String actual = legacyHash(oldSalt, pw);
        boolean ok = MessageDigest.isEqual(oldHash.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            setPassword(pw);
            legacySp.edit().remove("hidden_pin_hash").remove("hidden_pin_salt").apply();
        }
        return ok;
    }

    private static String legacyHash(String salt, String pw) {
        try {
            byte[] out = MessageDigest.getInstance("SHA-256").digest((salt + ":" + pw).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : out) hex.append(String.format(Locale.US, "%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String hash(String salt, String pw) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 30000, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Milliseconds the vault stays locked out after too many wrong attempts (0 = not locked). */
    long lockRemaining() { return Math.max(0, sp.getLong("lock_until", 0) - System.currentTimeMillis()); }

    void resetFails() { sp.edit().putInt("fails", 0).putLong("lock_until", 0).apply(); }

    void registerFail() {
        int f = sp.getInt("fails", 0) + 1;
        SharedPreferences.Editor e = sp.edit().putInt("fails", f);
        if (f >= 5) {
            e.putLong("lock_until", System.currentTimeMillis() + 30000L * (1L << Math.min(4, (f - 5))));
        }
        e.apply();
    }
}
