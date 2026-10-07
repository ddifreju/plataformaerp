# Despertador do backend (Render gratuito)

O plano gratuito do Render desliga o backend depois de 15 minutos sem acesso
e ele leva uns 3 minutos para voltar. Para isso não acontecer:

1. **Principal — no próprio Supabase** (pg_cron + pg_net), criado em
   2026-10-07: a cada 10 minutos o banco chama
   `https://radar-api-navega.onrender.com/actuator/health`.
   - Conferir: `select * from net._http_response order by created desc limit 5;`
   - Desligar: `select cron.unschedule('acordar-radar');`
2. **Reserva — GitHub Actions** (`.github/workflows/manter-acordado.yml`). O
   agendador do GitHub atrasa muito (rodou 2 vezes em 13 horas), por isso
   não serve sozinho.

Quando o backend sair do Render gratuito (Oracle, plano pago etc.), desligue
os dois.

O frontend roda na Vercel (`https://plataformaerp.vercel.app/radar`), que
não dorme.
