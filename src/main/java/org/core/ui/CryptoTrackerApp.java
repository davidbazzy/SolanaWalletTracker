package org.core.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.core.processors.Processor;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main JavaFX Application for CryptoWalletTracker.
 * Provides a modern dark-themed UI for tracking Solana wallet holdings.
 */
public class CryptoTrackerApp extends Application {

    private static final Logger logger = Logger.getLogger(CryptoTrackerApp.class.getName());
    private static final String APP_TITLE = "Crypto Wallet Tracker";
    private static final int DEFAULT_WIDTH = 1200;
    private static final int DEFAULT_HEIGHT = 800;

    private Processor processor;
    private MainViewController mainViewController;

    @Override
    public void init() {
        // Initialize the processor singleton before UI loads
        logger.log(Level.INFO, "Initializing Processor...");
        processor = Processor.getInstance();
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Create the main view controller
            mainViewController = new MainViewController(processor);

            // Create the scene with the main layout
            Scene scene = new Scene(mainViewController.getRoot(), DEFAULT_WIDTH, DEFAULT_HEIGHT);

            // Load the dark theme CSS
            String cssPath = Objects.requireNonNull(
                    getClass().getResource("/styles/dark-theme.css")
            ).toExternalForm();
            scene.getStylesheets().add(cssPath);

            // Configure the primary stage
            primaryStage.setTitle(APP_TITLE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(600);

            // Handle window close
            primaryStage.setOnCloseRequest(event -> {
                logger.log(Level.INFO, "Application closing...");
                shutdown();
            });

            // Show the stage
            primaryStage.show();

            // Start the processor after UI is visible
            Platform.runLater(() -> {
                mainViewController.initialize();
                processor.startJavaFX(mainViewController::onWalletsLoaded, mainViewController::onMarketDataUpdated);
            });

            logger.log(Level.INFO, "Application started successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start application", e);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        logger.log(Level.INFO, "Shutting down application...");
        if (processor != null) {
            processor.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
