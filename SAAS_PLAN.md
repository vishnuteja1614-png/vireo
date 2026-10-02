# Vireo SaaS — credits instead of API keys

## The core idea

**Today:** user pastes their own OpenRouter key → you earn nothing, most users give up at that step.

**SaaS:** user buys credits → app calls *your* server → your server calls the AI with *your* key →
you charge more than it costs you. The key never leaves your server, so it can't be stolen from the APK.

```
   PHONE (Vireo APK)                YOUR SERVER                   AI PROVIDER
   ─────────────────                ───────────                   ───────────
   "Generate titles"  ──────────▶   check user's credits
   (sends login token,              deduct 1 credit        ──────▶  OpenRouter
    NO api key)                     call AI with YOUR key  ◀──────  (your account)
                     ◀──────────    return result
```

The three things you must have: **(1) login, (2) credit balance, (3) payment**.

---

## You do NOT need a desktop

Everything below is managed from a **phone browser**. No laptop, no terminal, no server to babysit.

| Job | Tool | How you manage it from a phone |
|---|---|---|
| Database + login | **Supabase** (free tier) | supabase.com dashboard in Chrome |
| Server code | **Supabase Edge Functions** | I write it → push to your GitHub → GitHub Actions deploys it automatically |
| Deployments | **GitHub Actions** | already working in your repo; you just tap "Run workflow" |
| Payments | **Google Play Billing** or **Razorpay** | Play Console / Razorpay dashboard, both mobile-friendly |
| Your AI key | stored as a **secret** | pasted once into the Supabase dashboard, never in the app |
| Monitoring | Supabase logs | dashboard shows usage, errors, revenue |

You already proved this model works: this whole Android app was built, compiled and
released without you touching a computer. The backend is the same pattern.

---

## Cost and margin (real numbers)

Using Gemini 2.0 Flash through OpenRouter, a full publish pack ≈ 2,000 tokens.

| Item | Your cost | Sell for | Margin |
|---|---|---|---|
| 1 publish pack (titles + description + tags + keywords) | ~₹0.40 | 1 credit | — |
| 1 AI thumbnail image | ~₹2.50 | 5 credits | — |
| 1 script + voiceover | ~₹0.60 (TTS is free) | 2 credits | — |
| **100 credits pack** | ~₹45 of AI usage | **₹199** | ~₹154 |
| **500 credits pack** | ~₹225 | **₹799** | ~₹574 |
| Free trial | 20 credits (~₹9) | ₹0 | acquisition cost |

Rough break-even: **~15 paying users/month** covers infrastructure (which is ₹0 on free tiers
until you have real traffic).

⚠️ **Play Store rule:** digital goods sold inside an app on Google Play *must* use
Google Play Billing, and Google takes **15%** (first $1M/year). If you distribute the APK
outside Play (your website / direct download), you can use Razorpay at ~2% instead.

---

## Free tiers you'd be living inside

| Service | Free allowance | When you'd outgrow it |
|---|---|---|
| Supabase | 50,000 monthly active users, 500 MB DB | thousands of users |
| Edge Functions | 500,000 calls/month | ~15,000 users |
| GitHub Actions | 2,000 build min/month | never, for this |
| Cloudflare (alternative) | 100,000 requests/day | similar |

So your only real cost at the start is **AI usage you've already been paid for**.

---

## Build order (what I'd do, in phases)

**Phase 1 — Backend skeleton (no payments yet)**
- Supabase project, `users` + `credits` + `usage_log` tables
- Edge Function `/ai` that checks credits, deducts, calls OpenRouter, logs
- Your OpenRouter key stored as a Supabase secret
- Everyone starts with 20 free credits

**Phase 2 — App side**
- Add Google / email login to the APK
- Replace "paste your API key" with "You have N credits"
- All AI calls routed to your server instead of OpenRouter directly
- Keep the "use my own key" option as a power-user toggle (costs you nothing)

**Phase 3 — Payments**
- Razorpay first (fastest, works with direct APK, ~2% fee, UPI support)
- Play Billing later if/when you publish on the Play Store

**Phase 4 — Abuse protection**
- Rate limits per user, device attestation, max spend per day
  (otherwise one scripted user can burn your whole balance)

---

## The honest risks

1. **You are now liable for the AI bill.** Set a hard spending cap on OpenRouter day one.
2. **Refunds and failed payments** need handling — Razorpay webhooks.
3. **Free-tier abuse**: 20 free credits × unlimited fake accounts = your money. Needs phone/Google-verified signup.
4. **You need an OpenRouter account with a credit card** — that's the one thing only you can do.

---

## What you need to get, before I can build it

1. A **Supabase account** (free, signup on phone) → send me the project URL + service key
2. An **OpenRouter account with some credit** loaded (₹500 is plenty to start)
3. A **Razorpay account** (needs PAN + bank details, Indian business/individual)

Give me #1 and #2 and I can build and deploy Phase 1 + 2 entirely from here.
