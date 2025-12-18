package org.core.ui;

/**
 * Launcher class that bypasses the JavaFX module system requirement (required VM arg changes).
 * Use this as your main class instead of CryptoTrackerApp when running from an IDE without configuring module options.
 */
public class CryptoWalletTrackerLauncher {
    public static void main(String[] args) {
        CryptoTrackerApp.main(args);
    }
}
