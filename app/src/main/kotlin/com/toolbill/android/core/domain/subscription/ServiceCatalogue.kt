package com.toolbill.android.core.domain.subscription

/**
 * A bundled catalogue entry.
 *
 * **Never carries an amount.** Prices change constantly and a bundled one is stale the moment it
 * ships -- the user would either correct a figure they never asked for, or worse, not notice it
 * was wrong. What is safe to bundle is what does not move: what a service is called, what it
 * bills in, how often, and what kind of tool it is.
 */
data class CatalogueEntry(
    val name: String,
    val currency: String,
    val cycle: BillingCycle,
    val category: String,
)

/**
 * The services the add sheet can pre-fill from, 210 of them.
 *
 * Weighted towards what a freelancer or solo founder actually pays for, and towards the markets
 * this app is aimed at -- which is why the entertainment and storage entries bill in rupees
 * while the developer tooling bills in dollars. Currency here is a default the user can change
 * on the same screen, never a claim about their account.
 */
val serviceCatalogue: List<CatalogueEntry> = listOf(
    // AI tools
    CatalogueEntry("Anthropic API", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("ChatGPT Plus", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("ChatGPT Pro", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Claude Max", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Claude Pro", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Copy.ai", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Cursor", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Descript", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("ElevenLabs", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Gemini Advanced", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("GitHub Copilot", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Hugging Face Pro", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Jasper", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Midjourney", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("OpenAI API", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Otter.ai", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Perplexity Pro", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Replicate", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Runway", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Suno", "USD", BillingCycle.MONTHLY, "AI tools"),

    // Hosting & infra
    CatalogueEntry("AWS", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Bluehost", "USD", BillingCycle.ANNUAL, "Hosting & infra"),
    CatalogueEntry("Cloudflare Pro", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Cloudways", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("DigitalOcean", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Firebase", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Fly.io", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Google Cloud", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Heroku", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Hetzner", "EUR", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Kinsta", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Linode", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Microsoft Azure", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("MongoDB Atlas", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Neon", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Netlify Pro", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("PlanetScale", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Railway", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Redis Cloud", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Render", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("SiteGround", "USD", BillingCycle.ANNUAL, "Hosting & infra"),
    CatalogueEntry("Supabase Pro", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Upstash", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Vercel Pro", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("WP Engine", "USD", BillingCycle.MONTHLY, "Hosting & infra"),

    // Analytics
    CatalogueEntry("Amplitude", "USD", BillingCycle.MONTHLY, "Analytics"),
    CatalogueEntry("Fathom Analytics", "USD", BillingCycle.MONTHLY, "Analytics"),
    CatalogueEntry("Google Analytics 360", "USD", BillingCycle.ANNUAL, "Analytics"),
    CatalogueEntry("Heap", "USD", BillingCycle.MONTHLY, "Analytics"),
    CatalogueEntry("Hotjar", "USD", BillingCycle.MONTHLY, "Analytics"),
    CatalogueEntry("Mixpanel", "USD", BillingCycle.MONTHLY, "Analytics"),
    CatalogueEntry("Plausible", "USD", BillingCycle.MONTHLY, "Analytics"),
    CatalogueEntry("PostHog", "USD", BillingCycle.MONTHLY, "Analytics"),
    CatalogueEntry("Segment", "USD", BillingCycle.MONTHLY, "Analytics"),
    CatalogueEntry("Umami Cloud", "USD", BillingCycle.MONTHLY, "Analytics"),

    // Email & marketing
    CatalogueEntry("ActiveCampaign", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Beehiiv", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Brevo", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Buffer", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Buttondown", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("ConvertKit", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Customer.io", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Hootsuite", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Klaviyo", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Later", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Loops", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Mailchimp", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("MailerLite", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Mailgun", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Postmark", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Resend", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("SendGrid", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Substack", "USD", BillingCycle.MONTHLY, "Email & marketing"),
    CatalogueEntry("Typefully", "USD", BillingCycle.MONTHLY, "Email & marketing"),

    // Productivity
    CatalogueEntry("Airtable", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Asana", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Cal.com", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Calendly", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("ClickUp", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Coda", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Evernote", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Fastmail", "USD", BillingCycle.ANNUAL, "Productivity"),
    CatalogueEntry("Google Workspace", "INR", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Grammarly", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Linear", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Loom", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Make", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Microsoft 365", "INR", BillingCycle.ANNUAL, "Productivity"),
    CatalogueEntry("Miro", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Monday.com", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("n8n Cloud", "EUR", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Notion", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Notion Plus", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Obsidian Sync", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Raycast Pro", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Setapp", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Slack", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Superhuman", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Things", "USD", BillingCycle.ANNUAL, "Productivity"),
    CatalogueEntry("Todoist", "USD", BillingCycle.ANNUAL, "Productivity"),
    CatalogueEntry("Trello", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Zapier", "USD", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Zoom", "USD", BillingCycle.MONTHLY, "Productivity"),

    // Design
    CatalogueEntry("Adobe Creative Cloud", "USD", BillingCycle.ANNUAL, "Design"),
    CatalogueEntry("Adobe Stock", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Affinity", "USD", BillingCycle.ANNUAL, "Design"),
    CatalogueEntry("Blender Cloud", "EUR", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Canva Pro", "INR", BillingCycle.ANNUAL, "Design"),
    CatalogueEntry("Envato Elements", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Figma Organization", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Figma Professional", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Framer", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Freepik", "EUR", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Iconscout", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("LottieFiles", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Procreate", "USD", BillingCycle.ANNUAL, "Design"),
    CatalogueEntry("Rive", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Shutterstock", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Sketch", "USD", BillingCycle.ANNUAL, "Design"),
    CatalogueEntry("Spline", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Unsplash+", "USD", BillingCycle.ANNUAL, "Design"),
    CatalogueEntry("Webflow", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Whimsical", "USD", BillingCycle.MONTHLY, "Design"),

    // Storage
    CatalogueEntry("Backblaze", "USD", BillingCycle.ANNUAL, "Storage"),
    CatalogueEntry("Box", "USD", BillingCycle.MONTHLY, "Storage"),
    CatalogueEntry("Dropbox", "USD", BillingCycle.MONTHLY, "Storage"),
    CatalogueEntry("Google One", "INR", BillingCycle.MONTHLY, "Storage"),
    CatalogueEntry("iCloud+", "INR", BillingCycle.MONTHLY, "Storage"),
    CatalogueEntry("MEGA", "EUR", BillingCycle.MONTHLY, "Storage"),
    CatalogueEntry("OneDrive", "INR", BillingCycle.ANNUAL, "Storage"),
    CatalogueEntry("pCloud", "USD", BillingCycle.ANNUAL, "Storage"),
    CatalogueEntry("Proton Drive", "EUR", BillingCycle.ANNUAL, "Storage"),
    CatalogueEntry("Sync.com", "USD", BillingCycle.ANNUAL, "Storage"),
    CatalogueEntry("Tresorit", "EUR", BillingCycle.ANNUAL, "Storage"),

    // Domains
    CatalogueEntry("BigRock", "INR", BillingCycle.ANNUAL, "Domains"),
    CatalogueEntry("Cloudflare Registrar", "USD", BillingCycle.ANNUAL, "Domains"),
    CatalogueEntry("Dynadot", "USD", BillingCycle.ANNUAL, "Domains"),
    CatalogueEntry("Gandi", "EUR", BillingCycle.ANNUAL, "Domains"),
    CatalogueEntry("GoDaddy", "INR", BillingCycle.ANNUAL, "Domains"),
    CatalogueEntry("Hover", "USD", BillingCycle.ANNUAL, "Domains"),
    CatalogueEntry("Name.com", "USD", BillingCycle.ANNUAL, "Domains"),
    CatalogueEntry("Namecheap", "USD", BillingCycle.ANNUAL, "Domains"),
    CatalogueEntry("Porkbun", "USD", BillingCycle.ANNUAL, "Domains"),

    // Development
    CatalogueEntry("Apple Developer Program", "USD", BillingCycle.ANNUAL, "Development"),
    CatalogueEntry("Better Stack", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Bitbucket", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Bitrise", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Bugsnag", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Checkly", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("CircleCI", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Codemagic", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Datadog", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Expo EAS", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("GitHub Pro", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("GitHub Team", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("GitLab Premium", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Google Play Developer", "USD", BillingCycle.ANNUAL, "Development"),
    CatalogueEntry("IntelliJ IDEA", "USD", BillingCycle.ANNUAL, "Development"),
    CatalogueEntry("JetBrains All Products", "USD", BillingCycle.ANNUAL, "Development"),
    CatalogueEntry("New Relic", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Postman", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("RevenueCat", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Rollbar", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Sentry", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Sourcegraph", "USD", BillingCycle.MONTHLY, "Development"),
    CatalogueEntry("Tower", "USD", BillingCycle.ANNUAL, "Development"),

    // Security
    CatalogueEntry("1Password", "USD", BillingCycle.MONTHLY, "Security"),
    CatalogueEntry("Auth0", "USD", BillingCycle.MONTHLY, "Security"),
    CatalogueEntry("Bitwarden", "USD", BillingCycle.ANNUAL, "Security"),
    CatalogueEntry("Clerk", "USD", BillingCycle.MONTHLY, "Security"),
    CatalogueEntry("Dashlane", "USD", BillingCycle.MONTHLY, "Security"),
    CatalogueEntry("ExpressVPN", "USD", BillingCycle.ANNUAL, "Security"),
    CatalogueEntry("LastPass", "USD", BillingCycle.MONTHLY, "Security"),
    CatalogueEntry("Mullvad", "EUR", BillingCycle.MONTHLY, "Security"),
    CatalogueEntry("NordVPN", "USD", BillingCycle.ANNUAL, "Security"),
    CatalogueEntry("Proton VPN", "EUR", BillingCycle.ANNUAL, "Security"),
    CatalogueEntry("Snyk", "USD", BillingCycle.MONTHLY, "Security"),
    CatalogueEntry("Tailscale", "USD", BillingCycle.MONTHLY, "Security"),
    CatalogueEntry("WorkOS", "USD", BillingCycle.MONTHLY, "Security"),

    // Entertainment
    CatalogueEntry("Amazon Prime", "INR", BillingCycle.ANNUAL, "Entertainment"),
    CatalogueEntry("Apple Music", "INR", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("Apple TV+", "INR", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("Audible", "INR", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("Crunchyroll", "INR", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("Disney+ Hotstar", "INR", BillingCycle.ANNUAL, "Entertainment"),
    CatalogueEntry("Kindle Unlimited", "INR", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("Netflix", "INR", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("PlayStation Plus", "INR", BillingCycle.ANNUAL, "Entertainment"),
    CatalogueEntry("SonyLIV", "INR", BillingCycle.ANNUAL, "Entertainment"),
    CatalogueEntry("Spotify", "INR", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("Twitch", "USD", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("Xbox Game Pass", "INR", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("YouTube Premium", "INR", BillingCycle.MONTHLY, "Entertainment"),
    CatalogueEntry("ZEE5", "INR", BillingCycle.ANNUAL, "Entertainment"),

    // Other
    CatalogueEntry("Deel", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("FreshBooks", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Gumroad", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Judge.me", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Lemon Squeezy", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Paddle", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Printful", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Qikink", "INR", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("QuickBooks", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Razorpay", "INR", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Shopify Basic", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Shopify Grow", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Stripe", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Wise Business", "USD", BillingCycle.ANNUAL, "Other"),
    CatalogueEntry("Xero", "USD", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Zoho Books", "INR", BillingCycle.ANNUAL, "Other"),
)

/**
 * Up to [limit] suggestions for what has been typed.
 *
 * Names starting with the query rank above names merely containing it, so typing "not" still
 * offers Notion first while "cloud" can still find iCloud+ and Cloudflare. At two hundred
 * entries a pure prefix match hides too much of the catalogue behind knowing how a name begins.
 */
fun suggestServices(query: String, limit: Int = 4): List<CatalogueEntry> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return emptyList()

    val prefix = serviceCatalogue.filter { it.name.startsWith(trimmed, ignoreCase = true) }
    val contains = serviceCatalogue.filter {
        !it.name.startsWith(trimmed, ignoreCase = true) &&
            it.name.contains(trimmed, ignoreCase = true)
    }
    return (prefix + contains).take(limit)
}

/** The catalogue entry for an exact name match, or null when it knows nothing about the name. */
fun catalogueEntryFor(name: String): CatalogueEntry? =
    serviceCatalogue.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

/** The catalogue's category for an exact name match. */
fun catalogueCategoryFor(name: String): Category? {
    val entry = catalogueEntryFor(name) ?: return null
    return Category.entries.firstOrNull { it.displayName == entry.category }
}
