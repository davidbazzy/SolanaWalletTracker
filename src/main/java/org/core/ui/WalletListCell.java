package org.core.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import org.core.accounts.Wallet;

/**
 * Custom ListCell for displaying wallets in the sidebar.
 * Shows wallet name, truncated address, and SOL balance.
 */
public class WalletListCell extends ListCell<Wallet> {

    private final VBox container;
    private final Label nameLabel;
    private final Label addressLabel;
    private final Label balanceLabel;

    public WalletListCell() {
        container = new VBox(3);
        container.setPadding(new Insets(5, 0, 5, 0));

        nameLabel = new Label();
        nameLabel.getStyleClass().add("wallet-cell-name");

        addressLabel = new Label();
        addressLabel.getStyleClass().add("wallet-cell-address");

        balanceLabel = new Label();
        balanceLabel.getStyleClass().add("wallet-cell-balance");

        container.getChildren().addAll(nameLabel, addressLabel, balanceLabel);
    }

    @Override
    protected void updateItem(Wallet wallet, boolean empty) {
        super.updateItem(wallet, empty);

        if (empty || wallet == null) {
            setGraphic(null);
            setText(null);
        } else {
            nameLabel.setText(wallet.getName());
            addressLabel.setText(truncateAddress(wallet.getAddress()));
            balanceLabel.setText(String.format("%.4f SOL", wallet.getSolBalance()));
            setGraphic(container);
        }
    }

    /**
     * Truncates a Solana address to show first 4 and last 4 characters.
     */
    private String truncateAddress(String address) {
        if (address == null || address.length() < 10) {
            return address;
        }
        return address.substring(0, 4) + "..." + address.substring(address.length() - 4);
    }
}
