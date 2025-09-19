package org.tradelite.service.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "daily_prices", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "date"}),
       indexes = {
           @Index(name = "idx_symbol_date", columnList = "symbol, date"),
           @Index(name = "idx_symbol", columnList = "symbol")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyPrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Column(nullable = false)
    private double price;
    
    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDate.now();
        }
    }
    
    public DailyPrice(String symbol, LocalDate date, double price) {
        this.symbol = symbol;
        this.date = date;
        this.price = price;
    }
}
