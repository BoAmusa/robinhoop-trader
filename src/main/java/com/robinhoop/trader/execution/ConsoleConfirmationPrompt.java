package com.robinhoop.trader.execution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ConsoleConfirmationPrompt implements ConfirmationPrompt {

    @Override
    public boolean confirm(String message) {
        System.out.print(message + " [y/N]: ");
        System.out.flush();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            return line != null && (line.equalsIgnoreCase("y") || line.equalsIgnoreCase("yes"));
        } catch (IOException e) {
            return false;
        }
    }
}
