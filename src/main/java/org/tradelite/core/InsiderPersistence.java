package org.tradelite.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.StockSymbol;

@Component
public class InsiderPersistence {

    public static final String PERSISTENCE_FILE_PATH = "config/insider-transactions.json";

    private final ObjectMapper objectMapper;

    @Autowired
    public InsiderPersistence(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void persistToFile(
            Map<StockSymbol, Map<String, Integer>> transactions, String filePath) {
        File file = new File(filePath);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, transactions);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write file for insider transactions", e);
        }
    }

    public List<InsiderTransactionHistoric> readFromFile(String filePath) {
        File file = new File(filePath);
        try {
            if (!file.exists()) {
                persistToFile(Map.of(), filePath);
            }
            Map<String, Map<String, Integer>> map =
                    objectMapper.readValue(file, new TypeReference<>() {});

            return map.entrySet().stream()
                    .map(
                            e ->
                                    StockSymbol.fromString(e.getKey())
                                            .map(
                                                    symbol ->
                                                            new InsiderTransactionHistoric(
                                                                    symbol, e.getValue())))
                    .flatMap(Optional::stream)
                    .toList();

        } catch (IOException | IllegalStateException e) {
            throw new IllegalStateException("Failed to read file for insider transactions", e);
        }
    }
}
