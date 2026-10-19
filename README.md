# Voice Order & Dues Tracker

**Team:** Smart Tech  
**Status:** Backend MVP and demo UI in progress

## Problem

Small shop owners often receive customer orders through spoken conversations, calls, and Hindi or Hinglish voice notes. Turning these informal requests into accurate records takes time, and details like item quantities, advances, and outstanding balances can be missed or misremembered.

Shop owners need a simple way to turn voice or text orders into records they can review and correct, then track payments and dues with confidence.

## Planned solution

A web app that turns a typed transcript or voice note into an editable order draft. The shop owner reviews the customer, items, amounts, and dates before confirming. Confirmed orders can track partial payments and outstanding balances.

## Planned MVP

- Typed transcript entry and voice-note transcription
- AI-assisted extraction of customer and order details
- Review and correction before saving
- Order and partial-payment tracking
- Outstanding-dues summary
- Editable reminder drafts; no automatic message sending

## Current status

The Spring Boot backend and a lightweight browser demo are available. Open the application at
`http://localhost:8080/` to enter a transcript, review extracted order details, confirm an order,
record payments, and view outstanding dues. The demo uses the existing `/api/drafts` and
`/api/orders` routes and does not send WhatsApp or payment messages automatically.

Run the backend with:

```bash
mvn spring-boot:run
```

Run the tests with:

```bash
mvn test
```

## AI usage

AI is being used to support research, requirements planning, and product development. The team will document the tools and prompts actually used during the build.

Extraction uses Gemini through the Spring AI OpenAI-compatible client by default. Set `GEMINI_API_KEY` in the environment. To use the real OpenAI endpoint instead, set these application properties:

```properties
app.extraction.mode=openai
spring.ai.openai.base-url=https://api.openai.com
spring.ai.openai.chat.completions-path=/v1/chat/completions
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=${OPENAI_MODEL:gpt-4o-mini}
```

For a deterministic no-provider demo, set `app.extraction.mode=fake`; fake mode is off by default. The configured model client allows two attempts with a 250 ms backoff, bounding transient provider retries.

## Repository

This is the public team repository for the AI-First Startup Hackathon.
