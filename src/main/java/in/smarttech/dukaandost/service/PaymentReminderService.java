package in.smarttech.dukaandost.service;

import in.smarttech.dukaandost.domain.NoteLanguage;
import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.domain.Order;
import in.smarttech.dukaandost.domain.OrderStatus;
import in.smarttech.dukaandost.domain.ReminderAction;
import in.smarttech.dukaandost.repository.OwnerRepository;
import in.smarttech.dukaandost.security.CurrentOwner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentReminderService {

    private final OrderPaymentService orderPaymentService;
    private final ReminderLogService reminderLogService;
    private final OwnerRepository ownerRepository;
    private final CurrentOwner currentOwner;
    private final String shopName;
    private final String upiId;

    public PaymentReminderService(
            OrderPaymentService orderPaymentService,
            ReminderLogService reminderLogService,
            OwnerRepository ownerRepository,
            CurrentOwner currentOwner,
            @Value("${app.shop.name:}") String shopName,
            @Value("${app.payment.upi-id:}") String upiId) {
        this.orderPaymentService = Objects.requireNonNull(orderPaymentService);
        this.reminderLogService = Objects.requireNonNull(reminderLogService);
        this.ownerRepository = Objects.requireNonNull(ownerRepository);
        this.currentOwner = Objects.requireNonNull(currentOwner);
        this.shopName = shopName == null ? "" : shopName.strip();
        this.upiId = upiId == null ? "" : upiId.strip();
    }

    @Transactional
    public ReminderDraft draft(UUID orderId, String requestedLanguage) {
        OrderPaymentService.OrderSummary summary = orderPaymentService.receipt(orderId);
        Order order = summary.order();
        if (order.getStatus() != OrderStatus.CONFIRMED || summary.outstandingBalance().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A reminder is only available for a confirmed order with an outstanding balance.");
        }

        NoteLanguage preferred = supportedLanguage(order.getCustomer().getPreferredReminderLanguage());
        NoteLanguage language = preferred != null ? preferred : supportedLanguage(requestedLanguage);
        if (preferred == null && requestedLanguage != null && !requestedLanguage.isBlank() && language == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown reminder language. Choose Hindi, Hinglish, English, Marathi, Bengali or Tamil.");
        }
        if (language == null || language == NoteLanguage.AUTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a language for this reminder.");
        }

        String ownerShopName = ownerRepository.findById(currentOwner.id())
                .map(Owner::getShopName)
                .filter(value -> !value.isBlank())
                .map(String::strip)
                .orElse(shopName);
        String message = render(language, order.getCustomer().getName(),
                summary.outstandingBalance(), order.getDueDate(), ownerShopName, upiId, order.getCurrency());
        reminderLogService.record(order.getId(), language, ReminderAction.DRAFT_CREATED);
        return new ReminderDraft(message, language, preferred != null);
    }

    public void recordOpened(UUID orderId, String languageValue, String channel) {
        NoteLanguage language = supportedLanguage(languageValue);
        if (language == null || language == NoteLanguage.AUTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a supported language for this reminder.");
        }
        ReminderAction action = switch (channel == null ? "" : channel.strip().toUpperCase(Locale.ROOT)) {
            case "WHATSAPP" -> ReminderAction.WHATSAPP_OPENED;
            case "SMS" -> ReminderAction.SMS_OPENED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose WhatsApp or SMS as the reminder channel.");
        };
        reminderLogService.record(orderId, language, action);
    }

    private static NoteLanguage supportedLanguage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String language = value.strip().toLowerCase(Locale.ROOT);
        return switch (language) {
            case "auto" -> null;
            case "hi", "hindi", "हिन्दी", "हिंदी" -> NoteLanguage.HINDI;
            case "hinglish" -> NoteLanguage.HINGLISH;
            case "en", "english" -> NoteLanguage.ENGLISH;
            case "mr", "marathi", "मराठी" -> NoteLanguage.MARATHI;
            case "bn", "bengali", "বাংলা" -> NoteLanguage.BENGALI;
            case "ta", "tamil", "தமிழ்" -> NoteLanguage.TAMIL;
            default -> null;
        };
    }

    private static String render(NoteLanguage language, String customerName,
                                 BigDecimal remaining, LocalDate dueDate,
                                 String shopName, String upiId, String currency) {
        String name = customerName == null || customerName.isBlank() ? "" : customerName.strip();
        String amount = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"))
                .format(remaining);
        String due = dueDate == null ? ""
                : dueDate.format(DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH));
        String body = switch (language) {
            case HINDI -> "नमस्ते" + named(name) + ", आपके ऑर्डर की बाकी राशि ₹" + amount
                    + " है। जब सुविधाजनक हो, कृपया भुगतान कर दें।"
                    + (due.isEmpty() ? "" : " यदि संभव हो तो " + due + " तक भुगतान कर दें।")
                    + " धन्यवाद।";
            case HINGLISH -> "Namaste" + named(name) + ", aapke order ki baaki rakam ₹" + amount
                    + " hai. Jab aapko suvidha ho, kripya payment kar dein."
                    + (due.isEmpty() ? "" : " Agar mumkin ho to " + due + " tak payment kar dein.")
                    + " Dhanyavaad.";
            case MARATHI -> "नमस्कार" + named(name) + ", तुमच्या ऑर्डरची बाकी रक्कम ₹" + amount
                    + " आहे. तुम्हाला सोयीचे असेल तेव्हा कृपया पैसे भरा."
                    + (due.isEmpty() ? "" : " शक्य असल्यास " + due + " पर्यंत भरा.")
                    + " धन्यवाद.";
            case BENGALI -> "নমস্কার" + named(name) + ", আপনার অর্ডারের বাকি টাকা ₹" + amount
                    + "। সুবিধামতো দয়া করে পেমেন্ট করবেন।"
                    + (due.isEmpty() ? "" : " সম্ভব হলে " + due + " তারিখের মধ্যে পেমেন্ট করবেন।")
                    + " ধন্যবাদ.";
            case TAMIL -> "வணக்கம்" + named(name) + ", உங்கள் ஆர்டருக்கான மீதித் தொகை ₹" + amount
                    + ". உங்களுக்கு வசதியானபோது செலுத்துங்கள்."
                    + (due.isEmpty() ? "" : " முடிந்தால் " + due + "க்குள் செலுத்துங்கள்.")
                    + " நன்றி.";
            case ENGLISH -> "Hello" + named(name) + ", the remaining amount for your order is ₹" + amount
                    + ". Please pay when convenient."
                    + (due.isEmpty() ? "" : " If possible, please pay by " + due + ".")
                    + " Thank you.";
            case AUTO -> throw new IllegalArgumentException("AUTO is not a reminder language.");
        };
        String shop = shopName.isBlank() ? "" : "\n— " + shopName;
        String upi = upiId.isBlank() || !"INR".equalsIgnoreCase(currency) ? "" : "\nPay via UPI: "
                + buildUpiLink(upiId, shopName, remaining, currency);
        return body + shop + upi;
    }

    public static String buildUpiLink(String upiId, String payeeName, BigDecimal amount, String currency) {
        if (upiId == null || upiId.isBlank() || amount == null || amount.signum() <= 0
                || !"INR".equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("A UPI ID and positive INR amount are required.");
        }
        StringBuilder link = new StringBuilder("upi://pay?pa=")
                .append(encodeQueryValue(upiId.strip()))
                .append("&am=").append(amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("&cu=INR");
        if (payeeName != null && !payeeName.isBlank()) {
            link.append("&pn=").append(encodeQueryValue(payeeName.strip()));
        }
        return link.toString();
    }

    private static String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String named(String name) {
        return name.isEmpty() ? "" : " " + name;
    }

    public record ReminderDraft(String message, NoteLanguage language, boolean usedCustomerPreference) {
    }
}
