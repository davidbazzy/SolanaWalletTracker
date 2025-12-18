package org.core.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;

/**
 * Dialog for adding a new wallet to track.
 * Collects wallet name and Solana address from user.
 */
public class AddWalletDialog extends Dialog<Pair<String, String>> {

    private final TextField nameField;
    private final TextField addressField;
    private final Label errorLabel;


    public AddWalletDialog() {
        setTitle("Add Wallet");
        setHeaderText("Enter wallet details");
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);

        // Create form fields
        nameField = new TextField();
        nameField.setPromptText("e.g., My Trading Wallet");
        nameField.setPrefWidth(350);

        addressField = new TextField();
        addressField.setPromptText("e.g., 7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU");
        addressField.setPrefWidth(350);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("value-negative");
        errorLabel.setVisible(false);

        // Create form layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        Label nameLabel = new Label("Wallet Name:");
        Label addressLabel = new Label("Wallet Address:");

        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(addressLabel, 0, 1);
        grid.add(addressField, 1, 1);
        grid.add(errorLabel, 0, 2, 2, 1);

        // Create buttons
        ButtonType addButtonType = new ButtonType("Add Wallet", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(addButtonType, cancelButtonType);

        // Style the add button
        Button addButton = (Button) getDialogPane().lookupButton(addButtonType);
        addButton.getStyleClass().add("button-primary");

        // Disable add button until valid input
        addButton.setDisable(true);

        // Add validation listeners
        nameField.textProperty().addListener((obs, oldVal, newVal) -> validateInput(addButton));

        addressField.textProperty().addListener((obs, oldVal, newVal) -> validateInput(addButton));

        getDialogPane().setContent(grid);

        // Set result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return Pair.of(nameField.getText().trim(), addressField.getText().trim());
            }
            return null;
        });

        // Focus name field on show
        setOnShown(event -> nameField.requestFocus());
    }

    private void validateInput(Button addButton) {
        String name = nameField.getText().trim();
        String address = addressField.getText().trim();

        boolean isValid = true;
        String errorMessage = "";

        if (name.isEmpty()) {
            isValid = false;
        } else if (address.isEmpty()) {
            isValid = false;
        } else if (!isValidSolanaAddress(address)) {
            isValid = false;
            errorMessage = "Invalid Solana address format (should be 32-44 characters)";
        }

        errorLabel.setText(errorMessage);
        errorLabel.setVisible(!errorMessage.isEmpty());
        addButton.setDisable(!isValid);
    }

    private boolean isValidSolanaAddress(String address) {
        // Solana addresses are base58 encoded and typically 32-44 characters
        if (address.length() < 32 || address.length() > 44) {
            return false;
        }
        // Check for valid base58 characters (no 0, O, I, l)
        return address.matches("^[1-9A-HJ-NP-Za-km-z]+$");
    }

    /**
     * Shows the dialog and returns the wallet name and address if provided.
     */
    public static Optional<Pair<String, String>> showAndWait(Stage owner) {
        AddWalletDialog dialog = new AddWalletDialog();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog.showAndWait();
    }
}
