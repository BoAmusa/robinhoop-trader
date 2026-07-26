package com.robinhoop.trader;

import com.robinhoop.trader.execution.AutoApproveConfirmationPrompt;
import com.robinhoop.trader.execution.ConfirmationPrompt;
import com.robinhoop.trader.execution.ConsoleConfirmationPrompt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MainTest {

    @Test
    void autoModeResolvesToAutoApprove() {
        ConfirmationPrompt prompt = Main.resolveConfirmationPrompt("auto");
        assertInstanceOf(AutoApproveConfirmationPrompt.class, prompt);
    }

    @Test
    void manualModeResolvesToConsolePrompt() {
        ConfirmationPrompt prompt = Main.resolveConfirmationPrompt("manual");
        assertInstanceOf(ConsoleConfirmationPrompt.class, prompt);
    }

    @Test
    void unrecognizedModeDefaultsToConsolePrompt() {
        ConfirmationPrompt prompt = Main.resolveConfirmationPrompt("something-else");
        assertInstanceOf(ConsoleConfirmationPrompt.class, prompt);
    }
}
