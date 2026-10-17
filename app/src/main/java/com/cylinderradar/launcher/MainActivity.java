package com.cylinderradar.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    Store store;
    LauncherView view;
    BroadcastReceiver pkgReceiver;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        view = new LauncherView(this, store);
        setContentView(view);
        Window w = getWindow();
        w.setStatusBarColor(0xFF010607);
        w.setNavigationBarColor(0xFF010807);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) { view.reload(); }
        };
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && Intent.ACTION_MAIN.equals(i.getAction())) view.onHome();
    }

    @Override protected void onResume() {
        super.onResume();
        // The vault was already locked before returning to the launcher.
        setSecure(false);
        view.resetClock();
    }

    @Override protected void onPause() {
        // If the private cylinder was visible, keep the task preview protected while it leaves.
        boolean wasVaultVisible = view != null && (view.vaultOpen || view.cur == LauncherView.VAULT || view.prev == LauncherView.VAULT);
        if (view != null) view.lockNow();
        if (wasVaultVisible) setSecure(true);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (!view.onBack()) { /* we are the home screen: nothing to do */ }
    }

    void setSecure(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    AlertDialog.Builder builder() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    // ---- launching ---------------------------------------------------------------------------
    void launch(AppItem it, float x, float y) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(it.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ActivityOptions o = ActivityOptions.makeScaleUpAnimation(view, (int) x, (int) y, 1, 1);
            startActivity(i, o.toBundle());
        } catch (Exception e) {
            toast("Could not open " + it.label);
        }
    }

    void appInfo(AppItem it) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it.pkg, null)));
        } catch (Exception e) { toast("Not available"); }
    }

    void uninstall(AppItem it) {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", it.pkg, null)));
        } catch (Exception e) { toast("Cannot uninstall this app"); }
    }

    // ---- menus -------------------------------------------------------------------------------
    void showAppMenu(final AppItem it, final int page) {
        final String[] items;
        if (page == LauncherView.VAULT) {
            items = new String[]{"Unhide (remove from vault)", "Open", "App info"};
        } else {
            boolean pinned = view.isPinned(it);
            items = new String[]{pinned ? "Unpin from Home" : "Pin to Home", "Hide in Vault", "App info", "Uninstall"};
        }
        builder().setTitle(it.label).setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dlg, int which) {
                if (page == LauncherView.VAULT) {
                    if (which == 0) view.unhide(it);
                    else if (which == 1) launch(it, view.getWidth() / 2f, view.getHeight() / 2f);
                    else appInfo(it);
                } else {
                    if (which == 0) { if (view.isPinned(it)) view.unpin(it); else view.pin(it); }
                    else if (which == 1) {
                        ensurePassword(new Runnable() { @Override public void run() { view.hide(it); toast(it.label + " hidden in vault"); } });
                    }
                    else if (which == 2) appInfo(it);
                    else uninstall(it);
                }
            }
        }).show();
    }

    void showSettings() {
        final boolean analog = store.analog();
        String[] items = {
                "হোম স্ক্রিনের অ্যাপ নির্বাচন",
                "হিডেন অ্যাপ ম্যানেজ করুন (PIN প্রয়োজন)",
                "রাডার / সিলিন্ডারের রং",
                analog ? "ঘড়ি: অ্যানালগ → ডিজিটাল" : "ঘড়ি: ডিজিটাল → অ্যানালগ",
                store.hasPassword() ? "হিডেন পাসওয়ার্ড পরিবর্তন" : "হিডেন পাসওয়ার্ড সেট করুন",
                "ডিফল্ট লঞ্চার সেটিংস",
                "জেসচার ও ব্যবহারবিধি"
        };
        builder().setTitle("Cylinder Radar • Controls").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dlg, int which) {
                if (which == 0) chooseHomeApps();
                else if (which == 1) manageVaultApps();
                else if (which == 2) chooseColor();
                else if (which == 3) { store.setAnalog(!analog); view.invalidate(); }
                else if (which == 4) changePassword();
                else if (which == 5) {
                    try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
                    catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                } else showHelp();
            }
        }).setNegativeButton("বন্ধ", null).show();
    }

    /** Bulk-select favourite apps for the first cylinder. Hidden apps are never offered here. */
    private void chooseHomeApps() {
        if (view.allApps.isEmpty()) {
            toast("অ্যাপের তালিকা প্রস্তুত হচ্ছে—একটু পরে আবার চেষ্টা করুন।");
            return;
        }
        final ArrayList<AppItem> candidates = new ArrayList<>();
        final Set<String> hiddenNow = new HashSet<>(store.list("hidden"));
        final Set<String> seenPackages = new HashSet<>();
        final Set<String> pinnedPackages = new HashSet<>();
        for (AppItem it : view.allApps) if (store.contains("pinned", it.key)) pinnedPackages.add(it.pkg);
        for (AppItem it : view.allApps) {
            if (!hiddenNow.contains(it.key) && seenPackages.add(it.pkg)) candidates.add(it);
        }
        if (candidates.isEmpty()) {
            toast("কোনো সাধারণ অ্যাপ পাওয়া যায়নি।");
            return;
        }
        final String[] labels = new String[candidates.size()];
        final boolean[] checked = new boolean[candidates.size()];
        final Set<String> chosen = new HashSet<>();
        for (int i = 0; i < candidates.size(); i++) {
            AppItem it = candidates.get(i);
            labels[i] = it.label;
            checked[i] = pinnedPackages.contains(it.pkg);
            if (checked[i]) chosen.add(it.key);
        }
        builder().setTitle("হোম স্ক্রিনে কোন অ্যাপ থাকবে?")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        String key = candidates.get(which).key;
                        if (isChecked) chosen.add(key); else chosen.remove(key);
                    }
                })
                .setPositiveButton("সংরক্ষণ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        ArrayList<String> selected = new ArrayList<>();
                        for (AppItem it : candidates) if (chosen.contains(it.key)) selected.add(it.key);
                        store.saveList("pinned", selected);
                        view.rebuildPages();
                        toast("হোম স্ক্রিনের অ্যাপ সংরক্ষণ হয়েছে");
                    }
                })
                .setNegativeButton("বাতিল", null).show();
    }

    /** Vault inventory is only shown after authentication; the existing PBKDF2 and lockout stay in use. */
    private void manageVaultApps() {
        if (view.vaultOpen) showVaultAppsDialog();
        else authenticate(new Runnable() {
            @Override public void run() { showVaultAppsDialog(); }
        });
    }

    private void showVaultAppsDialog() {
        if (view.allApps.isEmpty()) {
            toast("অ্যাপের তালিকা প্রস্তুত হচ্ছে—একটু পরে আবার চেষ্টা করুন।");
            return;
        }
        final ArrayList<AppItem> candidates = new ArrayList<>();
        final Set<String> seenPackages = new HashSet<>();
        final Set<String> chosenPackages = new HashSet<>();
        final Set<String> savedHidden = new HashSet<>(store.list("hidden"));
        for (AppItem it : view.allApps) {
            if (seenPackages.add(it.pkg)) candidates.add(it);
            if (savedHidden.contains(it.key)) chosenPackages.add(it.pkg);
        }
        final String[] labels = new String[candidates.size()];
        final boolean[] checked = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            AppItem it = candidates.get(i);
            labels[i] = it.label;
            checked[i] = chosenPackages.contains(it.pkg);
        }
        builder().setTitle("হিডেন অ্যাপ নির্বাচন • ব্যক্তিগত")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        String pkg = candidates.get(which).pkg;
                        if (isChecked) chosenPackages.add(pkg); else chosenPackages.remove(pkg);
                    }
                })
                .setPositiveButton("সংরক্ষণ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        ArrayList<String> hidden = new ArrayList<>();
                        for (AppItem it : view.allApps) if (chosenPackages.contains(it.pkg)) hidden.add(it.key);
                        store.saveList("hidden", hidden);
                        ArrayList<String> pinned = new ArrayList<>(store.list("pinned"));
                        for (AppItem it : view.allApps) if (chosenPackages.contains(it.pkg)) pinned.remove(it.key);
                        store.saveList("pinned", pinned);
                        view.rebuildPages();
                        toast("হিডেন অ্যাপের তালিকা সংরক্ষণ হয়েছে");
                    }
                })
                .setNegativeButton("বাতিল", null).show();
    }

    void chooseColor() {
        builder().setTitle("Accent color")
                .setSingleChoiceItems(LauncherView.COLOR_NAMES, store.accent(), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int which) {
                        store.setAccent(which);
                        view.invalidate();
                        dlg.dismiss();
                    }
                }).setNegativeButton("Close", null).show();
    }

    void showHelp() {
        builder().setTitle("Gestures")
                .setMessage("• সিলিন্ডারের ওপর ডানে/বামে টেনে ঘোরান; ছেড়ে দিলে ইনর্শিয়া ও স্ন্যাপ কাজ করবে।\n\n"
                        + "• ওপর/নিচে সোয়াইপ করলে HOME ও ALL APPS সিলিন্ডার বদলাবে।\n\n"
                        + "• ঘড়ি বা নিচের পেজ-লেবেলের অংশে বামে সোয়াইপ করলে পরের সিলিন্ডার আসবে।\n\n"
                        + "• ঘড়ি বা নিচের পেজ-লেবেলের অংশে দ্রুত পরপর ডানে দুইবার সোয়াইপ করলে VAULT-এর পাসওয়ার্ড চাইবে।\n\n"
                        + "• অ্যাপ আইকনে লং-প্রেস করলে Pin/Unpin, Hide, App info ও Uninstall পাওয়া যাবে।\n\n"
                        + "• Settings থেকে একসঙ্গে Home apps বাছাই, Vault apps ম্যানেজ এবং Digital/Analog clock বদলানো যায়।\n\n"
                        + "• VAULT অ্যাপ খুললে, Back/Home চাপলে বা লঞ্চার ছাড়লে লক হবে; ভুল পাসওয়ার্ড বারবার দিলে সাময়িক বিরতি থাকবে।\n\n"
                        + "• সিস্টেম/MIUI Hidden Apps তালিকা আলাদাভাবে এখানে বেছে নিতে হবে; এই Vault ফোনের অন্য লঞ্চার থেকে অ্যাপ লুকায় না।")
                .setPositiveButton("OK", null).show();
    }

    // ---- vault password ----------------------------------------------------------------------
    private EditText passField(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setSingleLine(true);
        return et;
    }

    private View padded(View v) {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(24), dp(8), dp(24), dp(4));
        f.addView(v);
        return f;
    }

    /** Runs {@code ok} only after the correct vault password was entered (or created on first use). */
    void authenticate(final Runnable ok) {
        if (!store.hasPassword()) { createPassword("Create vault password", ok); return; }
        long wait = store.lockRemaining();
        if (wait > 0) { toast("Too many attempts. Try again in " + (wait / 1000 + 1) + "s"); return; }
        final EditText et = passField("Password");
        final AlertDialog dlg = builder().setTitle("Vault").setView(padded(et))
                .setPositiveButton("Unlock", null).setNegativeButton("Cancel", null).create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (store.check(et.getText().toString())) {
                    store.resetFails();
                    dlg.dismiss();
                    ok.run();
                } else {
                    store.registerFail();
                    et.setText("");
                    long w = store.lockRemaining();
                    if (w > 0) { dlg.dismiss(); toast("Too many attempts. Try again in " + (w / 1000 + 1) + "s"); }
                    else et.setError("Wrong password");
                }
            }
        });
    }

    void ensurePassword(final Runnable then) {
        if (store.hasPassword()) then.run();
        else createPassword("Create vault password", then);
    }

    void createPassword(String title, final Runnable then) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final EditText a = passField("New password (min 4 characters)");
        final EditText b = passField("Repeat password");
        box.addView(a);
        box.addView(b);
        final AlertDialog dlg = builder().setTitle(title).setView(padded(box))
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String p1 = a.getText().toString(), p2 = b.getText().toString();
                if (p1.length() < 4) { a.setError("At least 4 characters"); return; }
                if (!p1.equals(p2)) { b.setError("Passwords do not match"); return; }
                store.setPassword(p1);
                dlg.dismiss();
                toast("Vault password saved");
                if (then != null) then.run();
            }
        });
    }

    void changePassword() {
        if (!store.hasPassword()) { createPassword("Create vault password", null); return; }
        authenticate(new Runnable() {
            @Override public void run() { createPassword("New vault password", null); }
        });
    }
}
