package org.core;

import org.core.processors.Processor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MainFrame extends JFrame {

    private final Processor processor;
    private final JTextArea outputArea;
    private final JTextField inputField;

    public MainFrame() {
        setTitle("Solana Wallet Processor");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        processor = new Processor();
        processor.start();

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        add(new JScrollPane(outputArea), BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.addActionListener(e -> {
            String input = inputField.getText();
            inputField.setText("Input wallet name and address using the format: <wallet_name>:<wallet_address>\n");
            processor.handleWalletAddressInput(outputArea, input);
        });
        add(inputField, BorderLayout.SOUTH);
    }



    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
