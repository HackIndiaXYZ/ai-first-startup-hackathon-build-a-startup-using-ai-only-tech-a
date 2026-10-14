package in.smarttech.dukaandost.validation;

import java.math.BigDecimal;

public record ExtractedOrderItem(
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
