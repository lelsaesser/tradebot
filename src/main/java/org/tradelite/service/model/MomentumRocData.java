package org.tradelite.service.model;

import lombok.Data;

/**
 * Stores momentum ROC state for a symbol to enable crossover detection between calculation cycles.
 *
 * <p>This class persists the previous ROC values so that when the next calculation is performed, we
 * can detect if the ROC has crossed the zero line (momentum direction change).
 */
@Data
public class MomentumRocData {

    /** Previous 10-day ROC value */
    private double previousRoc10;

    /** Previous 20-day ROC value */
    private double previousRoc20;

    /** Whether this symbol has been initialized (has previous values) */
    private boolean initialized;
}
