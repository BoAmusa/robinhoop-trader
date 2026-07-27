package com.robinhoop.trader.marketdata;

import com.robinhoop.trader.model.Bar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileMarketDataClientTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesFiltersAndSortsBarsForASymbol() throws IOException {
        Path file = writeFile(
                "AAPL,2026-01-03,10,11,9,10.5,1000",
                "AAPL,2026-01-01,8,9,7,8.5,900",
                "AAPL,2026-01-02,9,10,8,9.5,950",
                "MSFT,2026-01-02,100,101,99,100.5,500"
        );

        FileMarketDataClient client = new FileMarketDataClient(file);
        List<Bar> bars = client.getDailyHistory("AAPL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));

        assertEquals(3, bars.size());
        assertEquals(LocalDate.of(2026, 1, 1), bars.get(0).date());
        assertEquals(LocalDate.of(2026, 1, 2), bars.get(1).date());
        assertEquals(LocalDate.of(2026, 1, 3), bars.get(2).date());
    }

    @Test
    void filtersOutBarsOutsideTheRequestedRange() throws IOException {
        Path file = writeFile(
                "AAPL,2026-01-01,8,9,7,8.5,900",
                "AAPL,2026-01-05,10,11,9,10.5,1000",
                "AAPL,2026-01-10,11,12,10,11.5,1100"
        );

        FileMarketDataClient client = new FileMarketDataClient(file);
        List<Bar> bars = client.getDailyHistory("AAPL", LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 6));

        assertEquals(1, bars.size());
        assertEquals(LocalDate.of(2026, 1, 5), bars.get(0).date());
    }

    @Test
    void symbolLookupIsCaseInsensitive() throws IOException {
        Path file = writeFile("aapl,2026-01-01,8,9,7,8.5,900");

        FileMarketDataClient client = new FileMarketDataClient(file);
        List<Bar> bars = client.getDailyHistory("AAPL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertEquals(1, bars.size());
    }

    @Test
    void throwsForASymbolWithNoData() throws IOException {
        Path file = writeFile("AAPL,2026-01-01,8,9,7,8.5,900");

        FileMarketDataClient client = new FileMarketDataClient(file);

        assertThrows(MarketDataException.class,
                () -> client.getDailyHistory("TSLA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void skipsMalformedLinesInsteadOfFailingTheWholeFile() throws IOException {
        Path file = writeFile(
                "AAPL,2026-01-01,8,9,7,8.5,900",
                "this is not a valid line",
                "AAPL,not-a-date,8,9,7,8.5,900",
                "AAPL,2026-01-02,9,10,8,9.5,950"
        );

        FileMarketDataClient client = new FileMarketDataClient(file);
        List<Bar> bars = client.getDailyHistory("AAPL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

        assertEquals(2, bars.size());
    }

    @Test
    void throwsWhenTheFileDoesNotExist() {
        Path missing = tempDir.resolve("does-not-exist.csv");

        assertThrows(MarketDataException.class, () -> new FileMarketDataClient(missing));
    }

    private Path writeFile(String... lines) throws IOException {
        Path file = tempDir.resolve("market-data-" + System.nanoTime() + ".csv");
        Files.write(file, List.of(lines));
        return file;
    }
}
