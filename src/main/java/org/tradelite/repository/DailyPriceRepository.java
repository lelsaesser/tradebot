package org.tradelite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tradelite.service.model.DailyPrice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {
    
    /**
     * Find all prices for a specific symbol, ordered by date ascending
     */
    List<DailyPrice> findBySymbolOrderByDateAsc(String symbol);
    
    /**
     * Find all prices for a specific symbol, ordered by date descending
     */
    List<DailyPrice> findBySymbolOrderByDateDesc(String symbol);
    
    /**
     * Find the most recent N prices for a symbol (for RSI calculations)
     */
    @Query("SELECT dp FROM DailyPrice dp WHERE dp.symbol = :symbol ORDER BY dp.date DESC")
    List<DailyPrice> findTopNBySymbolOrderByDateDesc(@Param("symbol") String symbol);
    
    /**
     * Find a specific price for a symbol on a date
     */
    Optional<DailyPrice> findBySymbolAndDate(String symbol, LocalDate date);
    
    /**
     * Get the latest 200 prices for a symbol (for RSI calculation stability)
     */
    @Query("SELECT dp FROM DailyPrice dp WHERE dp.symbol = :symbol ORDER BY dp.date DESC LIMIT 200")
    List<DailyPrice> findLatest200PricesBySymbol(@Param("symbol") String symbol);
    
    /**
     * Count total prices for a symbol
     */
    long countBySymbol(String symbol);
    
    
    /**
     * Get all unique symbols that have price data
     */
    @Query("SELECT DISTINCT dp.symbol FROM DailyPrice dp ORDER BY dp.symbol")
    List<String> findAllUniqueSymbols();
}
