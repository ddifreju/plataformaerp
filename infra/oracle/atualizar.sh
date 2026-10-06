#!/usr/bin/env bash
# Deploy automático: roda a cada 2 minutos (timer radar-atualizar do
# systemd, criado pelo instalar.sh). Se a main do GitHub mudou, baixa,
# reconstrói só o que mudou e reinicia. Se nada mudou, não faz nada.
# Log: journalctl -u radar-atualizar -n 50
set -euo pipefail

PASTA=/opt/radar
cd "$PASTA"

git fetch --quiet origin main
ATUAL=$(git rev-parse HEAD)
NOVA=$(git rev-parse origin/main)
if [ "$ATUAL" = "$NOVA" ] && [ "${1:-}" != "--forcar" ]; then
  exit 0
fi

echo "Atualizando de ${ATUAL:0:7} para ${NOVA:0:7}..."
git reset --quiet --hard origin/main
cd infra/oracle
# Monta as imagens novas antes de trocar: o sistema antigo segue no ar
# durante o build e só reinicia no fim (poucos segundos fora).
docker compose --env-file .env build
docker compose --env-file .env up -d --remove-orphans
# Limpa imagens velhas para o disco não encher com o tempo.
docker image prune -f >/dev/null
echo "Pronto: ${NOVA:0:7} no ar."
