package in.smarttech.dukaandost.web;

import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.service.PaymentCaptureService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment-capture")
public class PaymentCaptureController {

    private final PaymentCaptureService capture;

    public PaymentCaptureController(PaymentCaptureService capture) {
        this.capture = capture;
    }

    @PostMapping("/extract")
    @ResponseStatus(HttpStatus.OK)
    public PaymentCaptureService.PaymentCaptureReview extract(@Valid @RequestBody ExtractRequest request) {
        return capture.extract(request.transcript(), request.language());
    }

    public record ExtractRequest(@NotBlank @Size(max = 5000) String transcript, NoteLanguage language) {
    }
}
