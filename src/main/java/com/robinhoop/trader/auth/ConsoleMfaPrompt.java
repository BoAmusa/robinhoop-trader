package com.robinhoop.trader.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ConsoleMfaPrompt implements MfaPrompt {

    @Override
    public String promptForCode(String challengeType) {
        System.out.print("Enter the " + challengeType + " verification code from Robinhood: ");
        System.out.flush();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                throw new AuthException("No verification code entered");
            }
            return line.trim();
        } catch (IOException e) {
            throw new AuthException("Failed to read verification code from console", e);
        }
    }
}
