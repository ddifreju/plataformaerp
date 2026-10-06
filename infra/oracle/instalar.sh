#!/usr/bin/env bash
# Instalação do Radar numa máquina Ubuntu (Oracle Cloud Always Free, ARM).
# Uso, dentro da máquina:
#   curl -fsSL https://raw.githubusercontent.com/ddifreju/plataformaerp/main/infra/oracle/instalar.sh -o instalar.sh
#   sudo bash instalar.sh
#
# Pode rodar de novo sem medo: o que já foi feito é pulado. As senhas são
# perguntadas aqui no terminal (nunca vão para o git nem para o chat) e
# ficam só em /opt/radar/infra/oracle/.env, com permissão 600.
set -euo pipefail

PASTA=/opt/radar
REPO=https://github.com/ddifreju/plataformaerp.git

passo() { printf '\n\033[1;34m==> %s\033[0m\n' "$1"; }
erro() { printf '\n\033[1;31mERRO: %s\033[0m\n' "$1"; exit 1; }

[ "$(id -u)" -eq 0 ] || erro "rode com sudo: sudo bash instalar.sh"

passo "1/7 Instalando Docker, Git e o firewall persistente"
export DEBIAN_FRONTEND=noninteractive
echo iptables-persistent iptables-persistent/autosave_v4 boolean true | debconf-set-selections
echo iptables-persistent iptables-persistent/autosave_v6 boolean true | debconf-set-selections
apt-get update -qq
apt-get install -y -qq docker.io docker-compose-v2 git curl iptables-persistent >/dev/null
systemctl enable --now docker >/dev/null
usermod -aG docker ubuntu 2>/dev/null || true

passo "2/7 Memória extra (swap de 4 GB) para os builds"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

passo "3/7 Liberando as portas 80 e 443 no firewall da máquina"
# A imagem Ubuntu da Oracle bloqueia tudo menos SSH (22) por padrão.
for porta in 80 443; do
  iptables -C INPUT -p tcp --dport "$porta" -j ACCEPT 2>/dev/null \
    || iptables -I INPUT -p tcp --dport "$porta" -j ACCEPT
done
netfilter-persistent save >/dev/null

passo "4/7 Baixando o código do Radar"
if [ -d "$PASTA/.git" ]; then
  git -C "$PASTA" fetch --quiet origin main
  git -C "$PASTA" reset --quiet --hard origin/main
else
  git clone --quiet "$REPO" "$PASTA"
fi
ENV="$PASTA/infra/oracle/.env"

passo "5/7 Configuração (as mesmas do serviço radar-api-navega no Render)"
if [ -f "$ENV" ]; then
  echo "Já existe $ENV, mantendo como está."
else
  echo "Abra o Render > serviço radar-api-navega > Environment e copie os valores."
  echo "Para colar no terminal: clique com o botão direito (PowerShell) ou Ctrl+Shift+V."
  echo "As senhas não aparecem enquanto você cola: é normal, cole e aperte Enter."
  pergunta() {
    local nome="$1" oculto="$2" valor=""
    while [ -z "$valor" ]; do
      if [ "$oculto" = sim ]; then
        read -r -s -p "  $nome: " valor; echo >&2
      else
        read -r -p "  $nome: " valor
      fi
      valor="$(printf '%s' "$valor" | tr -d '\r' | sed 's/^ *//;s/ *$//')"
      case "$valor" in *"'"*) echo "    O valor não pode ter aspas simples; confira e cole de novo." >&2; valor="";; esac
    done
    printf '%s' "$valor"
  }
  URL=$(pergunta SPRING_DATASOURCE_URL nao)
  case "$URL" in jdbc:postgresql://*) ;; *) erro "a URL deve começar com jdbc:postgresql:// (rode de novo)";; esac
  USUARIO=$(pergunta SPRING_DATASOURCE_USERNAME nao)
  SENHA=$(pergunta SPRING_DATASOURCE_PASSWORD sim)
  HMAC=$(pergunta APP_DOCUMENTO_HMAC_CHAVE sim)
  IP=$(curl -fsS --max-time 10 https://api.ipify.org || curl -fsS --max-time 10 https://ifconfig.me)
  [ -n "$IP" ] || erro "não consegui descobrir o IP público da máquina"
  umask 077
  cat > "$ENV" <<EOF
SPRING_DATASOURCE_URL='$URL'
SPRING_DATASOURCE_USERNAME='$USUARIO'
SPRING_DATASOURCE_PASSWORD='$SENHA'
APP_DOCUMENTO_HMAC_CHAVE='$HMAC'
DOMINIO='${IP//./-}.sslip.io'
EOF
  chmod 600 "$ENV"
  echo "Configuração salva em $ENV (só o administrador da máquina lê)."
fi
DOMINIO=$(grep '^DOMINIO=' "$ENV" | cut -d"'" -f2)

passo "6/7 Montando e subindo o Radar (a primeira vez leva uns 10 a 15 minutos)"
cd "$PASTA/infra/oracle"
docker compose --env-file .env build
docker compose --env-file .env up -d --remove-orphans

passo "7/7 Ligando o deploy automático (a cada merge na main)"
chmod +x "$PASTA/infra/oracle/atualizar.sh"
cat > /etc/systemd/system/radar-atualizar.service <<EOF
[Unit]
Description=Atualiza o Radar quando a main do GitHub muda
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=$PASTA/infra/oracle/atualizar.sh
EOF
cat > /etc/systemd/system/radar-atualizar.timer <<EOF
[Unit]
Description=Confere a main do GitHub a cada 2 minutos

[Timer]
OnBootSec=1min
OnUnitActiveSec=2min

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now radar-atualizar.timer >/dev/null

echo
echo "Esperando o certificado HTTPS e o servidor responderem (até 5 minutos)..."
for _ in $(seq 60); do
  if curl -fsS -o /dev/null --max-time 10 "https://$DOMINIO/api/sessao" 2>/dev/null \
    || [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://$DOMINIO/api/sessao")" = 401 ]; then
    printf '\n\033[1;32mPRONTO! Abra: https://%s/radar\033[0m\n\n' "$DOMINIO"
    exit 0
  fi
  sleep 5
done
echo "O sistema subiu, mas o endereço ainda não respondeu."
echo "Confira as regras de entrada das portas 80 e 443 na Oracle (Security List) e veja:"
echo "  sudo docker compose -f $PASTA/infra/oracle/docker-compose.yml logs --tail 50"
echo "Endereço esperado: https://$DOMINIO/radar"
