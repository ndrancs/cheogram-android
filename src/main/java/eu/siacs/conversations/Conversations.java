package eu.siacs.conversations;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import de.gultsch.common.TrustManagers;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.EmojiInitializationService;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.ThemeHelper;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.Collection;
import javax.net.ssl.X509TrustManager;
import org.conscrypt.Conscrypt;

public class Conversations extends Application {

    @SuppressLint("StaticFieldLeak")
    private static Context CONTEXT;

    public static Context getContext() {
        return Conversations.CONTEXT;
    }

    private final Supplier<Collection<DatabaseBackend.AccountWithOptions>>
            accountWithOptionsSupplier =
                    () -> {
                        final var stopwatch = Stopwatch.createStarted();

                        final var accounts =
                                DatabaseBackend.getInstance(Conversations.this)
                                        .getAccountWithOptions();
                        Log.d(
                                Config.LOGTAG,
                                "fetching accounts from database in " + stopwatch.stop());
                        return accounts;
                    };
    private Supplier<Collection<DatabaseBackend.AccountWithOptions>> accountWithOptions =
            Suppliers.memoize(accountWithOptionsSupplier);

    private final Supplier<X509TrustManager> filteredDefaultTrustManager =
            Suppliers.memoize(
                    () -> {
                        try {
                            return TrustManagers.createDefaultTrustManager();
                        } catch (final NoSuchAlgorithmException
                                | KeyStoreException
                                | CertificateException
                                | IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
    @Override
    public void onCreate() {
        super.onCreate();
        installSecurityProvider();
        CONTEXT = this.getApplicationContext();
        EmojiInitializationService.execute(getApplicationContext());
        ExceptionHelper.init(getApplicationContext());
        applyThemeSettings();
    }

    private static void installSecurityProvider() {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        } catch (final Throwable throwable) {
            Log.e(Config.LOGTAG, "could not install security provider", throwable);
        }
    }

    public static Conversations getInstance(final Context context) {
        if (context.getApplicationContext() instanceof Conversations c) {
            return c;
        }
        throw new IllegalStateException("Application is not Conversations");
    }

    public void applyThemeSettings() {
        final var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences == null) {
            return;
        }
        applyThemeSettings(sharedPreferences);
    }

    private void applyThemeSettings(final SharedPreferences sharedPreferences) {
        AppCompatDelegate.setDefaultNightMode(getDesiredNightMode(this, sharedPreferences));
        var dynamicColorsOptions =
                new DynamicColorsOptions.Builder()
                        .setPrecondition((activity, t) -> isDynamicColorsDesired(activity))
                        .build();
        DynamicColors.applyToActivitiesIfAvailable(this, dynamicColorsOptions);
    }

    public static int getDesiredNightMode(final Context context) {
        final var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences == null) {
            return AppCompatDelegate.getDefaultNightMode();
        }
        return getDesiredNightMode(context, sharedPreferences);
    }

    public static boolean isDynamicColorsDesired(final Context context) {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (isCustomColorsDesired(context)) return false;
        return preferences.getBoolean(AppSettings.DYNAMIC_COLORS, false);
    }

    public static boolean isCustomColorsDesired(final Context context) {
        final var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String theme =
                sharedPreferences.getString(AppSettings.THEME, context.getString(R.string.theme));
        return "custom".equals(theme);
    }

    private static int getDesiredNightMode(
            final Context context, final SharedPreferences sharedPreferences) {
        var theme =
                sharedPreferences.getString(AppSettings.THEME, context.getString(R.string.theme));

        // Migrate old themes to equivalent custom
        if ("oledblack".equals(theme)) {
            theme = "custom";
            final var p = PreferenceManager.getDefaultSharedPreferences(context);
            p
                .edit()
                .putString(AppSettings.THEME, "custom")
                .putBoolean("custom_theme_automatic", false)
                .putBoolean("custom_theme_dark", true)
                .putBoolean("custom_theme_color_match", true)
                .putInt("custom_dark_theme_primary", context.getColor(R.color.white))
                .putInt("custom_dark_theme_primary_dark", context.getColor(android.R.color.black))
                .putInt("custom_dark_theme_accent", context.getColor(R.color.yeller))
                .putInt("custom_dark_theme_background_primary", context.getColor(android.R.color.black))
                .commit();
        }

        // Migrate old themes to equivalent custom
        if ("obsidian".equals(theme)) {
            theme = "custom";
            final var p = PreferenceManager.getDefaultSharedPreferences(context);
            p
                .edit()
                .putString(AppSettings.THEME, "custom")
                .putBoolean("custom_theme_automatic", false)
                .putBoolean("custom_theme_dark", true)
                .putInt("custom_dark_theme_primary", context.getColor(R.color.black_perpy))
                .putInt("custom_dark_theme_primary_dark", context.getColor(R.color.black_perpy))
                .putInt("custom_dark_theme_accent", context.getColor(R.color.yeller))
                .putInt("custom_dark_theme_background_primary", context.getColor(R.color.blacker_perpy))
                .commit();
        }

        if ("custom".equals(theme)) {
            if (sharedPreferences.getBoolean("custom_theme_automatic", false)) return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            return sharedPreferences.getBoolean("custom_theme_dark", false) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        }

        return getDesiredNightMode(theme);
    }

    public static int getDesiredNightMode(final String theme) {
        if ("automatic".equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else if ("light".equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
    }

    public void resetAccounts() {
        this.accountWithOptions = Suppliers.memoize(accountWithOptionsSupplier);
    }

    public Collection<DatabaseBackend.AccountWithOptions> getAccounts() {
        return this.accountWithOptions.get();
    }

    public boolean hasEnabledAccount() {
        return Iterables.any(
                getAccounts(),
                a ->
                        !a.isOptionSet(Account.OPTION_DISABLED)
                                && !a.isOptionSet(Account.OPTION_SOFT_DISABLED));
    }

    public X509TrustManager getFilteredDefaultTrustManager() {
        return this.filteredDefaultTrustManager.get();
    }
}
