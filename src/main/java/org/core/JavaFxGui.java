package org.core;


import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.core.processors.ProcessorJavaFX;

// TODO: Temporarily do not use until I fix display issues. Currently using Mainframe.java
public class JavaFxGui extends Application {

    ProcessorJavaFX processor = new ProcessorJavaFX();

    // TODO: Sort logging as all text is red and not in a single line

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Crypto Wallet Viewer");

        // Input Field
        TextField walletInput = new TextField();
        walletInput.setPromptText("Enter wallet address...");
        walletInput.setPrefWidth(300);

        // Buttons
        Button submitButton = new Button("Submit");
        Button holdingsButton = new Button("Holdings");
        Button analyseButton = new Button("Analyse");
        Button exitButton = new Button("Exit");

        // Text Area for Output
        TextArea outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setPrefHeight(150);
        outputArea.setPromptText("Results will be displayed here...");

        // Styling
        String buttonStyle = "-fx-font-size: 14px; -fx-text-fill: white;";
        submitButton.setStyle(buttonStyle + "-fx-background-color: #4CAF50;");
        holdingsButton.setStyle(buttonStyle + "-fx-background-color: #2196F3;");
        analyseButton.setStyle(buttonStyle + "-fx-background-color: #FF9800;");
        exitButton.setStyle(buttonStyle + "-fx-background-color: #f44336;");

        // Button Actions
        /*submitButton.setOnAction(e -> {
            outputArea.clear();
            processor.handleWalletAddressInput(outputArea, walletInput.getText());
            walletInput.clear();
            //System.out.println("Fetching wallet data for: " + walletInput.getText());
        });

        holdingsButton.setOnAction(e -> {
            outputArea.clear();
            processor.handleHoldings(outputArea);
            //outputArea.appendText("📊 Wallet Holdings:\n- BTC: 0.5\n- ETH: 2.3\n- SOL: 15.8\n");
        });

        analyseButton.setOnAction(e -> {
            outputArea.clear();
            processor.handleSharedTokens(outputArea);
            outputArea.appendText("📈 Portfolio Analysis:\n- Total Value: $12,450\n- Daily Change: +3.2%\n- Diversification Score: 8/10\n");
        });

        exitButton.setOnAction(e -> primaryStage.close());*/

        // Layout
        VBox layout = new VBox(10);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #2c3e50;");

        HBox buttonRow = new HBox(10, submitButton, holdingsButton, analyseButton, exitButton);
        buttonRow.setAlignment(Pos.CENTER);

        layout.getChildren().addAll(walletInput, buttonRow, outputArea);

        // Scene Setup
        Scene scene = new Scene(layout, 500, 350);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}


