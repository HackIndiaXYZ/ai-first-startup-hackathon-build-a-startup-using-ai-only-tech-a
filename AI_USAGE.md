# AI usage log

- OpenAI quota restrictions blocked live extraction integration testing. The extraction provider was switched to Google's Gemini API free tier so the six acceptance transcripts can be exercised without changing the extraction and validation business rules.
- Gemini is accessed through Google's OpenAI-compatible endpoint using the existing Spring AI OpenAI starter. The default model is `gemini-3.8-flash`; the API key is read from `GEMINI_API_KEY`.
- A configurable fake extraction mode is available for local demos and tests. It is disabled by default.
- Retry backoff config was invalid (multiplier 1, then max-interval not above initial-interval) and was fixed: max-attempts=2, initial 250ms, multiplier 2, max 2s.
