package in.smarttech.dukaandost.web;

import in.smarttech.dukaandost.security.CurrentOwner;
import in.smarttech.dukaandost.service.CustomerLedgerService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerLedgerController {

    private final CustomerLedgerService ledgers;
    private final CurrentOwner currentOwner;

    public CustomerLedgerController(CustomerLedgerService ledgers, CurrentOwner currentOwner) {
        this.ledgers = ledgers;
        this.currentOwner = currentOwner;
    }

    @GetMapping("/{customerId}/ledger")
    public CustomerLedgerService.CustomerLedger ledger(@PathVariable UUID customerId) {
        currentOwner.id();
        return ledgers.ledger(customerId);
    }
}
