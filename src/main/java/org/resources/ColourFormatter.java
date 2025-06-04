package org.resources;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class ColourFormatter extends Formatter {

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";

    @Override
    public String format(LogRecord record) {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String colour = switch (record.getLevel().getName()) {
            case "SEVERE" -> RED;
            case "WARNING" -> YELLOW;
            case "INFO" -> BLUE;
            case "CONFIG" -> CYAN;
            case "FINE", "FINER", "FINEST" -> GREEN;
            default -> RESET;
        };

        //return colour + formatMessage(record) + RESET + "\n";
        return colour + String.format("[%s] [%s] [%s] %s%n",
                sdf.format(new Date(record.getMillis())), // Timestamp
                record.getSourceClassName(),              // Class name
                record.getLevel(),                        // Log level
                record.getMessage());                     // Log message
    }
}
