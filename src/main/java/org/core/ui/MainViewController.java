package org.core.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.apache.commons.lang3.tuple.Pair;
import org.core.accounts.Position;
import org.core.accounts.Token;
import org.core.accounts.Wallet;
import org.core.processors.Processor;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main view controller for the CryptoWalletTracker JavaFX application.
 * Manages the entire UI layout and interactions.
 */
public class MainViewController {

    private static final Logger logger = Logger.getLogger(MainViewController.class.getName());
    private static final double MIN_DISPLAY_VALUE = 100.0; // Filter positions below $100
    private static final double MIN_OVERLAP_VALUE = 10000.0; // Filter overlapping tokens below $10k

    private final Processor processor;
    private final BorderPane root;

    // UI Components
    private ListView<Wallet> walletListView;
    private TableView<PositionRow> holdingsTable;
    private TableView<OverlapRow> overlapTable;
    private Label statusLabel;
    private Label lastUpdateLabel;
    private Label totalValueLabel;
    private Label walletCountLabel;
    private ProgressIndicator loadingIndicator;
    private Button addWalletButton;

    // Data
    private final ObservableList<Wallet> walletList = FXCollections.observableArrayList();
    private final ObservableList<PositionRow> positionRows = FXCollections.observableArrayList();
    private final ObservableList<OverlapRow> overlapRows = FXCollections.observableArrayList();

    private final NumberFormat currencyFormat;
    private final DateTimeFormatter timeFormatter;

    public MainViewController(Processor processor) {
        this.processor = processor;
        this.currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        this.root = createLayout();
    }

    public BorderPane getRoot() {
        return root;
    }

    public void initialize() {
        logger.log(Level.INFO, "Initializing MainViewController...");
        setStatus("Loading all stored wallets...", true);
    }

    private BorderPane createLayout() {
        BorderPane borderPane = new BorderPane();
        borderPane.getStyleClass().add("root");

        // Header
        borderPane.setTop(createHeader());

        // Sidebar
        borderPane.setLeft(createSidebar());

        // Main content
        borderPane.setCenter(createMainContent());

        // Footer
        borderPane.setBottom(createFooter());

        return borderPane;
    }

    private HBox createHeader() {
        HBox header = new HBox(15);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Crypto Wallet Tracker");
        titleLabel.getStyleClass().add("title-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(20, 20);
        loadingIndicator.setVisible(false);

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-ready");

        header.getChildren().addAll(titleLabel, spacer, loadingIndicator, statusLabel);

        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(250);
        sidebar.setMinWidth(200);

        Label walletsLabel = new Label("Wallets");
        walletsLabel.getStyleClass().add("header-label");

        walletCountLabel = new Label("0 wallets");
        walletCountLabel.getStyleClass().add("label-secondary");

        // Wallet list
        walletListView = new ListView<>(walletList);
        walletListView.setCellFactory(lv -> new WalletListCell());
        walletListView.setPlaceholder(new Label("No wallets added yet"));
        VBox.setVgrow(walletListView, Priority.ALWAYS);

        // Selection listener
        walletListView.getSelectionModel().selectedItemProperty().addListener((obs, oldWallet, newWallet) -> {
            if (newWallet != null) {
                displayWalletHoldings(newWallet);
            }
        });

        // Add wallet button
        addWalletButton = new Button("+ Add Wallet");
        addWalletButton.getStyleClass().add("button-primary");
        addWalletButton.setMaxWidth(Double.MAX_VALUE);
        addWalletButton.setOnAction(e -> showAddWalletDialog());

        // Show all wallets button
        Button showAllButton = new Button("Show All Holdings");
        showAllButton.setMaxWidth(Double.MAX_VALUE);
        showAllButton.setOnAction(e -> displayAllHoldings());

        sidebar.getChildren().addAll(
                walletsLabel,
                walletCountLabel,
                walletListView,
                addWalletButton,
                showAllButton
        );

        return sidebar;
    }

    private TabPane createMainContent() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Holdings tab
        Tab holdingsTab = new Tab("Holdings");
        holdingsTab.setContent(createHoldingsTable());

        // Overlapping tokens tab
        Tab overlapTab = new Tab("Overlapping Tokens");
        overlapTab.setContent(createOverlapTable());

        tabPane.getTabs().addAll(holdingsTab, overlapTab);

        // Update overlap data when tab is selected
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == overlapTab) {
                updateOverlapTable();
            }
        });

        return tabPane;
    }

    @SuppressWarnings("unchecked")
    private VBox createHoldingsTable() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(10));

        holdingsTable = new TableView<>(positionRows);
        holdingsTable.setPlaceholder(new Label("Select a wallet to view holdings"));
        VBox.setVgrow(holdingsTable, Priority.ALWAYS);

        // Token Name column
        TableColumn<PositionRow, String> nameCol = new TableColumn<>("Token Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("tokenName"));
        nameCol.setPrefWidth(180);

        // Ticker column
        TableColumn<PositionRow, String> tickerCol = new TableColumn<>("Ticker");
        tickerCol.setCellValueFactory(new PropertyValueFactory<>("ticker"));
        tickerCol.setPrefWidth(100);

        // Balance column
        TableColumn<PositionRow, String> balanceCol = new TableColumn<>("Balance");
        balanceCol.setCellValueFactory(new PropertyValueFactory<>("balance"));
        balanceCol.setPrefWidth(150);
        balanceCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // USD Price column
        TableColumn<PositionRow, String> priceCol = new TableColumn<>("USD Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("usdPrice"));
        priceCol.setPrefWidth(120);
        priceCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // USD Value column
        TableColumn<PositionRow, String> valueCol = new TableColumn<>("USD Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("usdValue"));
        valueCol.setPrefWidth(130);
        valueCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // Mint Address column
        TableColumn<PositionRow, String> mintCol = new TableColumn<>("Mint Address");
        mintCol.setCellValueFactory(new PropertyValueFactory<>("mintAddress"));
        mintCol.setPrefWidth(150);

        holdingsTable.getColumns().addAll(nameCol, tickerCol, balanceCol, priceCol, valueCol, mintCol);

        // Double-click to copy mint address
        holdingsTable.setRowFactory(tv -> {
            TableRow<PositionRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    copyToClipboard(row.getItem().getMintAddress());
                }
            });
            return row;
        });

        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyMint = new MenuItem("Copy Mint Address");
        copyMint.setOnAction(e -> {
            PositionRow selected = holdingsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                copyToClipboard(selected.getMintAddress());
            }
        });
        contextMenu.getItems().add(copyMint);
        holdingsTable.setContextMenu(contextMenu);

        Label hint = new Label("Double-click a row to copy mint address");
        hint.getStyleClass().add("label-muted");

        container.getChildren().addAll(holdingsTable, hint);
        return container;
    }

    @SuppressWarnings("unchecked")
    private VBox createOverlapTable() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(10));

        Label description = new Label("Tokens held in multiple wallets (minimum $10,000 total value)");
        description.getStyleClass().add("label-secondary");

        overlapTable = new TableView<>(overlapRows);
        overlapTable.setPlaceholder(new Label("No overlapping tokens found"));
        VBox.setVgrow(overlapTable, Priority.ALWAYS);

        // Token Name column
        TableColumn<OverlapRow, String> nameCol = new TableColumn<>("Token Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("tokenName"));
        nameCol.setPrefWidth(180);

        // Ticker column
        TableColumn<OverlapRow, String> tickerCol = new TableColumn<>("Ticker");
        tickerCol.setCellValueFactory(new PropertyValueFactory<>("ticker"));
        tickerCol.setPrefWidth(100);

        // Wallet Count column
        TableColumn<OverlapRow, Integer> countCol = new TableColumn<>("# Wallets");
        countCol.setCellValueFactory(new PropertyValueFactory<>("walletCount"));
        countCol.setPrefWidth(80);
        countCol.setStyle("-fx-alignment: CENTER;");

        // Total Value column
        TableColumn<OverlapRow, String> totalCol = new TableColumn<>("Total Value");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalValue"));
        totalCol.setPrefWidth(130);
        totalCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // Wallets column
        TableColumn<OverlapRow, String> walletsCol = new TableColumn<>("Wallets");
        walletsCol.setCellValueFactory(new PropertyValueFactory<>("walletNames"));
        walletsCol.setPrefWidth(300);

        overlapTable.getColumns().addAll(nameCol, tickerCol, countCol, totalCol, walletsCol);

        container.getChildren().addAll(description, overlapTable);
        return container;
    }

    private HBox createFooter() {
        HBox footer = new HBox(20);
        footer.getStyleClass().add("footer-bar");
        footer.setAlignment(Pos.CENTER_LEFT);

        Label totalLabel = new Label("Total Portfolio Value:");
        totalLabel.getStyleClass().add("label-secondary");

        totalValueLabel = new Label("$0.00");
        totalValueLabel.getStyleClass().add("total-value");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lastUpdateLabel = new Label("Last updated: --:--:--");
        lastUpdateLabel.getStyleClass().add("label-muted");

        footer.getChildren().addAll(totalLabel, totalValueLabel, spacer, lastUpdateLabel);

        return footer;
    }

    // === Event Handlers ===

    private void showAddWalletDialog() {
        Stage owner = (Stage) root.getScene().getWindow();
        Optional<Pair<String, String>> result = AddWalletDialog.showAndWait(owner);

        result.ifPresent(pair -> {
            String name = pair.getLeft();
            String address = pair.getRight();
            addWallet(name, address);
        });
    }

    private void addWallet(String name, String address) {
        setStatus("Adding wallet...", true);
        addWalletButton.setDisable(true);

        // Process wallet asynchronously
        processor.addWalletAsync(name, address, wallet -> Platform.runLater(() -> {
            if (wallet != null) {
                // Add to list if not already present
                boolean exists = walletList.stream()
                        .anyMatch(w -> w.getAddress().equals(wallet.getAddress()));
                if (!exists) {
                    walletList.add(wallet);
                    updateWalletCount();
                }
                walletListView.getSelectionModel().select(wallet);
                setStatus("Wallet added successfully", false);
            } else {
                setStatus("Failed to add wallet", false);
                showError("Failed to Add Wallet", "Could not retrieve wallet data. Please check the address and try again.");
            }
            addWalletButton.setDisable(false);
        }));
    }

    private void displayWalletHoldings(Wallet wallet) {
        positionRows.clear();

        int filteredCount = 0;
        int noMarketDataCount = 0;
        double totalValue = 0;

        for (Position position : wallet.getPositions().values()) {
            Token token = position.getToken();

            if (token.getMarketData() == null) {
                noMarketDataCount++;
                continue;
            }

            if (position.getUsdBalance() < MIN_DISPLAY_VALUE) {
                filteredCount++;
                continue;
            }

            positionRows.add(new PositionRow(position));
            totalValue += position.getUsdBalance();
        }

        // Sort by USD value descending
        positionRows.sort((a, b) -> Double.compare(b.getUsdValueRaw(), a.getUsdValueRaw()));

        totalValueLabel.setText(currencyFormat.format(totalValue));

        logger.log(Level.INFO, String.format(
                "Displaying %d positions for wallet %s (filtered: %d below $100, %d no market data)",
                positionRows.size(), wallet.getName(), filteredCount, noMarketDataCount
        ));
    }

    private void displayAllHoldings() {
        walletListView.getSelectionModel().clearSelection();
        positionRows.clear();

        double totalValue = 0;
        Map<String, Wallet> wallets = processor.getWallets();

        for (Wallet wallet : wallets.values()) {
            for (Position position : wallet.getPositions().values()) {
                Token token = position.getToken();

                if (token.getMarketData() == null) {
                    continue;
                }

                if (position.getUsdBalance() < MIN_DISPLAY_VALUE) {
                    continue;
                }

                positionRows.add(new PositionRow(position, wallet.getName()));
                totalValue += position.getUsdBalance();
            }
        }

        // Sort by USD value descending
        positionRows.sort((a, b) -> Double.compare(b.getUsdValueRaw(), a.getUsdValueRaw()));

        totalValueLabel.setText(currencyFormat.format(totalValue));
    }

    private void updateOverlapTable() {
        overlapRows.clear();

        Map<String, Wallet> wallets = processor.getWallets();
        Map<String, Token> tokenMap = processor.getTokenMap();
        Map<String, List<Position>> tokenToPositions = new HashMap<>();

        // Group positions by token mint address
        for (Wallet wallet : wallets.values()) {
            for (Position position : wallet.getPositions().values()) {
                String mintAddress = position.getToken().getMintAddress();
                tokenToPositions.computeIfAbsent(mintAddress, k -> new ArrayList<>()).add(position);
            }
        }

        // Filter for tokens in multiple wallets with significant value
        for (Map.Entry<String, List<Position>> entry : tokenToPositions.entrySet()) {
            List<Position> positions = entry.getValue();

            if (positions.size() <= 1) {
                continue;
            }

            double totalValue = positions.stream().mapToDouble(Position::getUsdBalance).sum();

            if (totalValue < MIN_OVERLAP_VALUE) {
                continue;
            }

            Token token = tokenMap.get(entry.getKey());
            if (token == null) {
                continue;
            }

            overlapRows.add(new OverlapRow(token, positions, wallets));
        }

        // Sort by total value descending
        overlapRows.sort((a, b) -> Double.compare(b.getTotalValueRaw(), a.getTotalValueRaw()));
    }

    // === Callbacks for Processor ===

    public void onWalletLoaded(Wallet wallet) {
        Platform.runLater(() -> {
            // Add wallet if not already present
            boolean exists = walletList.stream()
                    .anyMatch(w -> w.getAddress().equals(wallet.getAddress()));
            if (!exists) {
                walletList.add(wallet);
                updateWalletCount();
                setStatus("Loading all stored wallets... (" + wallet.getName() + ")", true);

                // Select the first wallet when it's added
                if (walletList.size() == 1) {
                    walletListView.getSelectionModel().selectFirst();
                }
            }
        });
    }

    public void onWalletsLoaded() {
        Platform.runLater(() -> {
            updateWalletCount();
            setStatus("Ready", false);

            if (!walletList.isEmpty() && walletListView.getSelectionModel().isEmpty()) {
                walletListView.getSelectionModel().selectFirst();
            }
        });
    }

    public void onMarketDataUpdated() {
        Platform.runLater(() -> {
            // Refresh current view
            Wallet selectedWallet = walletListView.getSelectionModel().getSelectedItem();
            if (selectedWallet != null) {
                displayWalletHoldings(selectedWallet);
            } else if (!positionRows.isEmpty()) {
                displayAllHoldings();
            }

            lastUpdateLabel.setText("Last updated: " + timeFormatter.format(LocalDateTime.now()));
        });
    }

    // === Helper Methods ===

    private void setStatus(String message, boolean loading) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-ready", "status-loading");
        statusLabel.getStyleClass().add(loading ? "status-loading" : "status-ready");
        loadingIndicator.setVisible(loading);
    }

    private void updateWalletCount() {
        int count = walletList.size();
        walletCountLabel.setText(count + (count == 1 ? " wallet" : " wallets"));
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        setStatus("Copied to clipboard", false);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // === Inner Classes for Table Rows ===

    public static class PositionRow {
        private final String tokenName;
        private final String ticker;
        private final String balance;
        private final String usdPrice;
        private final String usdValue;
        private final double usdValueRaw;
        private final String mintAddress;
        private final String walletName;

        private static final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        private static final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
        private static final java.text.DecimalFormat priceFormat = new java.text.DecimalFormat("$#,##0.0000000");

        public PositionRow(Position position) {
            this(position, null);
        }

        public PositionRow(Position position, String walletName) {
            Token token = position.getToken();
            this.tokenName = token.getName();
            this.ticker = token.getTicker();
            this.balance = numberFormat.format(position.getTokenBalance());
            this.mintAddress = token.getMintAddress();
            this.walletName = walletName;
            this.usdValueRaw = position.getUsdBalance();

            if (token.getMarketData() != null) {
                this.usdPrice = priceFormat.format(token.getMarketData().getUsdPrice());
                this.usdValue = currencyFormat.format(position.getUsdBalance());
            } else {
                this.usdPrice = "N/A";
                this.usdValue = "N/A";
            }
        }

        public String getTokenName() { return tokenName; }
        public String getTicker() { return ticker; }
        public String getBalance() { return balance; }
        public String getUsdPrice() { return usdPrice; }
        public String getUsdValue() { return usdValue; }
        public double getUsdValueRaw() { return usdValueRaw; }
        public String getMintAddress() { return mintAddress; }
        public String getWalletName() { return walletName; }
    }

    public static class OverlapRow {
        private final String tokenName;
        private final String ticker;
        private final int walletCount;
        private final String totalValue;
        private final double totalValueRaw;
        private final String walletNames;

        private static final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);

        public OverlapRow(Token token, List<Position> positions, Map<String, Wallet> wallets) {
            this.tokenName = token.getName();
            this.ticker = token.getTicker();
            this.walletCount = positions.size();
            this.totalValueRaw = positions.stream().mapToDouble(Position::getUsdBalance).sum();
            this.totalValue = currencyFormat.format(totalValueRaw);

            StringBuilder names = new StringBuilder();
            for (Position pos : positions) {
                Wallet wallet = wallets.get(pos.getWalletAddress());
                if (wallet != null) {
                    if (names.length() > 0) names.append(", ");
                    names.append(wallet.getName());
                }
            }
            this.walletNames = names.toString();
        }

        public String getTokenName() { return tokenName; }
        public String getTicker() { return ticker; }
        public int getWalletCount() { return walletCount; }
        public String getTotalValue() { return totalValue; }
        public double getTotalValueRaw() { return totalValueRaw; }
        public String getWalletNames() { return walletNames; }
    }
}
