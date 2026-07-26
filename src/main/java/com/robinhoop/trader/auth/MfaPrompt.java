package com.robinhoop.trader.auth;

public interface MfaPrompt {

    String promptForCode(String challengeType);
}
