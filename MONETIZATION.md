# Making money from Vireo — without ads

Puter pays the AI bill, so almost every rupee you collect is profit.
Your running cost is close to ₹0. That changes what works.

---

## The model that actually works: Free + Pro (freemium)

This is how CapCut, InShot, VN, VivaVideo and Canva all make money. Not ads — **upgrades**.

### What stays FREE (generous — this is your marketing)
- Full timeline editing, trim, split, speed
- Export up to 1080p **with a small Vireo watermark**
- 20 transitions, 20 caption styles
- AI Studio: 5 generations per day
- Best-upload-time engine, voiceover (TTS)

### What's PRO
| Feature | Why people pay |
|---|---|
| **No watermark** | #1 reason anyone upgrades. Creators can't post branded-by-someone-else video |
| **4K / 60fps export** | YouTubers need it |
| **All 125 transitions + 116 caption styles** | the "unlock everything" dopamine |
| **Unlimited AI** | publish packs, thumbnails, scripts |
| **Batch export** | post the same video to 5 platforms at once |
| **Project backup / cloud** | safety |
| **No queue / priority render** | perceived speed |

### Pricing for India (and global)
| Plan | India | Global | Notes |
|---|---|---|---|
| Monthly | **₹149** | $4.99 | impulse price |
| Yearly | **₹999** | $29.99 | best value, push this — 44% discount framing |
| **Lifetime** | **₹1,999** | $59.99 | huge early-stage cash, Indian users love it |

Expect **2–5%** of active users to convert on a good freemium video app.

---

## The maths

| Users | Paying @3% | Monthly revenue (avg ₹120/user) | Your cost |
|---|---|---|---|
| 1,000 | 30 | ₹3,600 | ~₹0 |
| 10,000 | 300 | ₹36,000 | ~₹0 |
| 50,000 | 1,500 | ₹1,80,000 | ~₹0 |
| 200,000 | 6,000 | ₹7,20,000 | ~₹0 |

Minus the store cut. **This is why Puter matters** — a normal AI app would be paying
₹2–5 per active user per month in API costs and could actually lose money at scale.

---

## Payment rails

| Route | Fee | Notes |
|---|---|---|
| **Google Play Billing** | 15% (first $1M/yr) | Mandatory if you sell inside an app listed on Play. Easiest trust + global cards |
| **Razorpay** (direct APK / website) | ~2% | UPI, way cheaper, but you must distribute outside Play |
| **Both** | — | Play for reach, website for margin. Many Indian apps do this |

Realistically: **launch on Play with Play Billing**. 15% is the cost of distribution and trust.

---

## 6 more income streams (no ads)

1. **Template packs** — ₹49–199 one-time. "Wedding pack", "Gym reels pack", "Festival pack".
   Highest margin thing you can sell; it's just JSON + presets.
2. **Caption style packs** — same idea, trivial to produce once the engine exists.
3. **Premium AI credits** — for things Puter doesn't cover (AI video generation).
   Sell at 3–4× cost. Only build this after the editor is loved.
4. **Creator Pro tier (₹499/mo)** — brand kit, logo watermark, team projects, analytics.
   Small audience, big ARPU.
5. **White-label / agency licence** — ₹25,000–1,00,000 one-time to social media agencies
   who want it branded as their own. One sale = 500 consumer subscriptions.
6. **Affiliate** — mic/tripod/lighting links inside the app, Amazon India ~3–8%.
   Low effort, small but free money.

---

## What NOT to do

- ❌ **Ads** — you already ruled them out, and they're right to avoid: they destroy
  the premium feel of an editor and pay terribly in India (~₹40–80 per 1,000 views).
- ❌ **Paid-up-front app** — kills installs. Nobody buys an unknown editor sight unseen.
- ❌ **Paywalling basic editing** — people will just uninstall. Paywall *output quality*
  and *volume*, never the core creation.
- ❌ **Charging for AI before the editor is good** — the editor is the product; AI is the hook.

---

## Build order for monetization

**Step 1 — Watermark + Pro flag (biggest lever, 1 build)**
- Add a small animated Vireo logo to free exports
- `isPro` boolean stored locally
- Gate 4K/60fps, lock transitions/captions past the first 20

**Step 2 — Paywall screen**
- Beautiful upgrade screen, 3 plans, "Most popular" on yearly
- Trigger it at the moment of pain: when they hit export with a watermark

**Step 3 — Google Play Billing**
- Needs: Play Console account (**$25 one-time**), company/individual details, bank account
- I wire the billing library and entitlement checks

**Step 4 — Template & caption packs**
- In-app catalogue, each pack a one-time purchase

**Step 5 — Analytics to find the leak**
- Where do people drop off before paying? Fix that, not the price.

---

## The uncomfortable truth

Monetization is the easy part. **Distribution is the hard part.**
10,000 real users is worth more than any pricing strategy. Budget as much energy
for getting installs (YouTube Shorts demos of the app itself, Instagram reels made
*with* the app, creator partnerships) as you spent building it.
