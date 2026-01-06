package org.tradelite.trading;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.TickerSymbol;
import org.tradelite.trading.model.Portfolio;
import org.tradelite.trading.model.Position;
import org.tradelite.trading.model.Transaction;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoTradingService {

    private static final double BUY_AMOUNT = 200.0;

    private final PortfolioPersistence portfolioPersistence;
    private final TelegramClient telegramClient;

    public void executeBuy(TickerSymbol ticker, double currentPrice, String reason) {
        try {
            Portfolio portfolio = portfolioPersistence.loadPortfolio();

            // Calculate quantity to buy based on fixed dollar amount
            double quantity = BUY_AMOUNT / currentPrice;

            // Execute buy
            Portfolio updatedPortfolio = portfolio.buy(ticker.getName(), quantity, currentPrice);

            // Save updated portfolio
            portfolioPersistence.savePortfolio(updatedPortfolio);

            // Record transaction
            Transaction transaction =
                    Transaction.createBuy(ticker.getName(), quantity, currentPrice, reason);
            portfolioPersistence.addTransaction(transaction);

            String message =
                    String.format(
                            """
                            🤖 Demo Trade Executed: BUY
                            Symbol: %s
                            Quantity: %.6f
                            Price: $%.2f
                            Total Cost: $%.2f
                            Remaining Cash: $%.2f
                            Reason: %s""",
                            ticker.getDisplayName(),
                            quantity,
                            currentPrice,
                            BUY_AMOUNT,
                            updatedPortfolio.getCashBalance(),
                            reason);

            telegramClient.sendMessage(message);

            log.info(
                    "Demo buy executed: {} x {} @ ${} (Total: ${})",
                    ticker.getName(),
                    quantity,
                    currentPrice,
                    BUY_AMOUNT);

        } catch (IllegalArgumentException e) {
            log.warn("Failed to execute buy for {}: {}", ticker.getName(), e.getMessage());
            telegramClient.sendMessage(
                    "⚠️ Demo Trade Failed: Unable to buy "
                            + ticker.getDisplayName()
                            + " - "
                            + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error executing buy for {}", ticker.getName(), e);
        }
    }

    public void executeSell(TickerSymbol ticker, double currentPrice, String reason) {
        try {
            Portfolio portfolio = portfolioPersistence.loadPortfolio();

            // Check if we have a position
            if (!portfolio.hasPosition(ticker.getName())) {
                log.debug("No position to sell for {}", ticker.getName());
                return;
            }

            Position position = portfolio.getPosition(ticker.getName());

            // Execute sell (100% of position)
            Portfolio updatedPortfolio = portfolio.sell(ticker.getName(), currentPrice);

            // Save updated portfolio
            portfolioPersistence.savePortfolio(updatedPortfolio);

            // Record transaction
            Transaction transaction =
                    Transaction.createSell(
                            ticker.getName(), position.getQuantity(), currentPrice, reason);
            portfolioPersistence.addTransaction(transaction);

            // Calculate profit/loss
            double profitLoss = position.getProfitLoss(currentPrice);
            double profitLossPercent = position.getProfitLossPercentage(currentPrice);

            String message =
                    String.format(
                            """
                            🤖 Demo Trade Executed: SELL
                            Symbol: %s
                            Quantity: %.6f
                            Sell Price: $%.2f
                            Buy Price: $%.2f
                            Total Proceeds: $%.2f
                            Profit/Loss: $%.2f (%.2f%%)
                            New Cash Balance: $%.2f
                            Reason: %s""",
                            ticker.getDisplayName(),
                            position.getQuantity(),
                            currentPrice,
                            position.getAveragePrice(),
                            position.getCurrentValue(currentPrice),
                            profitLoss,
                            profitLossPercent,
                            updatedPortfolio.getCashBalance(),
                            reason);

            telegramClient.sendMessage(message);

            log.info(
                    "Demo sell executed: {} x {} @ ${} (P/L: ${} / {}%)",
                    ticker.getName(),
                    position.getQuantity(),
                    currentPrice,
                    profitLoss,
                    profitLossPercent);

        } catch (IllegalArgumentException e) {
            log.warn("Failed to execute sell for {}: {}", ticker.getName(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error executing sell for {}", ticker.getName(), e);
        }
    }

    public Portfolio getPortfolio() {
        return portfolioPersistence.loadPortfolio();
    }
}
