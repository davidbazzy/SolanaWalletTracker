package org.core;

import org.apache.commons.lang3.StringUtils;
import org.core.processors.Processor;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private final Processor processor;
    private final JTextArea outputArea;
    private final JTextField inputField;

    public MainFrame() {
        setTitle("Solana Wallet Processor");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());


        outputArea = new JTextArea();
        outputArea.setEditable(false);
        add(new JScrollPane(outputArea), BorderLayout.CENTER);

        processor = Processor.getInstance();
        processor.start(outputArea);
        inputField = new JTextField();
        inputField.addActionListener(e -> {
            String input = inputField.getText();
            outputArea.setText(StringUtils.EMPTY);
            processor.handleWalletAddressInput(outputArea, input);
            inputField.setText(StringUtils.EMPTY);
        });
        add(inputField, BorderLayout.SOUTH);
    }



    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
