package in.smarttech.dukaandost.validation;

import in.smarttech.dukaandost.domain.OrderEntryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedOrder(
        String customerName,
        String customerPhone,
        String sourceNoteLanguage,
        String preferredReminderLanguage,
        OrderEntryType entryType,
        LocalDate orderDate,
        LocalDate dueDate,
        BigDecimal total,
        String currency,
        List<ExtractedOrderItem> items,
        BigDecimal advanceAmount,
        LocalDate advancePaidAt,
        String advanceNote,
        String transcript) {

    public ExtractedOrder {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
