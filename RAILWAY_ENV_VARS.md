# Railway Environment Variables Setup Guide

## Required Environment Variables on Railway

Go to your Railway project → select the Backend service → Variables tab

### Auto-provided by Railway PostgreSQL plugin
These are injected automatically when you add a PostgreSQL plugin:
- `DATABASE_URL` — full connection URL (postgresql://...)
- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`

### You must set JDBC_DATABASE_URL manually
Railway provides `DATABASE_URL` but Spring needs `jdbc:` prefix.
In Variables, add:
```
JDBC_DATABASE_URL = jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}
```
Or manually set the full JDBC URL:
```
JDBC_DATABASE_URL = jdbc:postgresql://your-host:5432/railway
```

### App variables (set these manually)
| Variable | Example | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates production config |
| `JWT_SECRET` | 64+ random chars | JWT signing key — use a random generator |
| `MAIL_USERNAME` | `you@gmail.com` | Gmail address for sending emails |
| `MAIL_PASSWORD` | `xxxx xxxx xxxx xxxx` | Gmail App Password (not your login password) |
| `STRIPE_SECRET_KEY` | `sk_live_...` | Stripe secret key from dashboard.stripe.com |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` | From Stripe webhooks dashboard |
| `TWILIO_ACCOUNT_SID` | `AC...` | From console.twilio.com |
| `TWILIO_AUTH_TOKEN` | `...` | From console.twilio.com |
| `TWILIO_WHATSAPP_FROM` | `whatsapp:+14155238886` | Twilio sandbox or approved number |
| `FRONTEND_URL` | `https://your-app.vercel.app` | Your deployed frontend URL (no trailing slash) |
| `APP_COMPANY_NAME` | `EduAssist` | Shown in emails |

### How to generate JWT_SECRET
```bash
node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"
```
Or use: https://generate-random.org/encryption-key-generator?count=1&bytes=64&cipher=sha-512

### Gmail App Password
1. Go to Google Account → Security → 2-Step Verification → App passwords
2. Create an app password for "Mail"
3. Use the 16-character code (with spaces) as MAIL_PASSWORD

### After deploying
1. Add PostgreSQL plugin in Railway dashboard
2. Set all variables above
3. Railway auto-deploys on git push
4. Backend URL will be: `https://your-service.up.railway.app`
5. Set your Angular app's `environment.prod.ts` apiUrl to that URL + `/api`
