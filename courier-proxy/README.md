# SPR ZONE BD — Private Courier Server Proxy

Keep **Steadfast** (`Api-Key`, `Secret-Key`) and **Pathao** (`Client ID`, `Client Secret`) strictly on your server—**never** inside `index.html`, `admin.html`, Android app code, or GitHub commits.

## Setup (Cloudflare Workers Free Tier)

1. Install Wrangler CLI and log in:
   ```bash
   npm install -g wrangler
   wrangler login
   ```
2. Set your **private** server secrets inside `courier-proxy/`:
   ```bash
   wrangler secret put STEADFAST_API_KEY
   wrangler secret put STEADFAST_SECRET_KEY
   ```
   Optional (if using Pathao direct merchant API):
   ```bash
   wrangler secret put PATHAO_CLIENT_ID
   wrangler secret put PATHAO_CLIENT_SECRET
   wrangler secret put PATHAO_USERNAME
   wrangler secret put PATHAO_PASSWORD
   wrangler secret put PATHAO_STORE_ID
   ```
3. Deploy the server proxy:
   ```bash
   wrangler deploy
   ```
4. Copy your deployed URL (e.g. `https://spr-zone-bd-courier-proxy.<your-name>.workers.dev`) into **Admin Portal → Payment & SMS API → Courier Pickup Server Proxy URL**.

Every dispatch & tracking request from `admin.html` automatically sends your signed-in Admin Firebase ID token (`mail.sajjadahmed@gmail.com`)—zero API secrets ever touch the browser.
