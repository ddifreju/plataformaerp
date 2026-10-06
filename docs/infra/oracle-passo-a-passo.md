# Radar na Oracle Cloud (grátis) — passo a passo

Objetivo: tirar o Radar do Render (que dorme e demora minutos para acordar)
e colocar numa máquina própria e gratuita da Oracle, que fica ligada o
tempo todo. O banco continua no Supabase (São Paulo), com os mesmos dados.

Tempo total: uns 40 a 60 minutos, a maior parte esperando.

## O que NÃO fazer (para nunca pagar nada)

- Não clicar em **Upgrade** / **Pay As You Go**. A conta fica no plano
  gratuito (Free Tier); assim a Oracle não cobra nada.
- Não criar nenhum outro serviço além da máquina deste guia (nada de
  "Autonomous Database", "Load Balancer", "Kubernetes" etc.).
- Ao criar a máquina, só aceitar opções com a etiqueta **Always Free**.

---

## Parte 1 — Criar a conta (10 min + espera)

1. Abra **https://www.oracle.com/br/cloud/free/** e clique em
   **Comece gratuitamente** (ou "Start for free").
2. Preencha país (**Brasil**), nome e e-mail. Confirme o e-mail que chegar.
3. Na tela seguinte:
   - **Senha**: anote num lugar seguro.
   - **Cloud Account Name**: um nome simples, ex. `radarerp` (é o "login
     da empresa"; anote também).
   - **Home Region**: **Brazil East (Sao Paulo)**. ⚠️ Não dá para trocar
     depois.
4. Endereço, telefone e **cartão de crédito**. O cartão é só verificação:
   pode aparecer uma cobrança pequena que é estornada. Tipo de conta:
   **Individual**.
5. Aguarde o e-mail **"Your account is ready"** (de minutos até algumas
   horas). Só siga quando ele chegar.

## Parte 2 — Alerta de gastos (cinto de segurança, 3 min)

1. Entre em **https://cloud.oracle.com** com o Cloud Account Name, e-mail
   e senha.
2. Menu ☰ (canto superior esquerdo) → **Billing & Cost Management** →
   **Budgets** → **Create Budget**.
3. Nome `alerta`, **Budget Amount** `1` (dólar), alerta em **100%** do
   valor **Actual**, e-mail: o seu. **Create**.

Se algum dia aparecer cobrança, você recebe e-mail na hora.

## Parte 3 — Criar a máquina (10 min)

1. Menu ☰ → **Compute** → **Instances** → **Create instance**.
2. **Name**: `radar`.
3. **Image and shape** → **Edit**:
   - **Change image** → **Canonical Ubuntu** → versão **24.04** →
     **Select image**.
   - **Change shape** → **Ampere** → marque **VM.Standard.A1.Flex**
     (tem a etiqueta *Always Free-eligible*).
   - **Number of OCPUs**: `2`. **Amount of memory (GB)**: `8`.
     → **Select shape**.
4. **Networking**: deixe **Create new virtual cloud network** e **Create
   new public subnet**. Confira que **Assign a public IPv4 address** está
   **ligado**.
5. **Add SSH keys**: escolha **Generate a key pair for me** e clique em
   **Save private key**. Guarde o arquivo baixado (termina em `.key`) na
   pasta **Downloads** e renomeie para `radar.key`. ⚠️ Sem esse arquivo
   não dá para entrar na máquina; não perca.
6. **Boot volume**: deixe como está (o grátis vai até 200 GB).
7. Clique em **Create**. Em 1 a 3 minutos o quadrado fica verde
   (**Running**).
   - Se aparecer **"Out of capacity"**: a Oracle está sem máquina livre
     naquele momento. Tente de novo em alguns minutos, ou troque o
     **Availability domain**, ou reduza para 1 OCPU e 6 GB. Não muda nada
     no resto do guia.
8. Na página da máquina, copie o **Public IP address** (ex.
   `152.67.10.20`). Vamos chamar de **SEU_IP**.

## Parte 4 — Abrir as portas do site na rede da Oracle (3 min)

1. Na página da máquina, procure **Primary VNIC** → clique no link da
   **Subnet**.
2. Abra **Security Lists** (às vezes na aba **Security**) → clique em
   **Default Security List for ...**.
3. **Add Ingress Rules**:
   - **Source CIDR**: `0.0.0.0/0`
   - **IP Protocol**: `TCP`
   - **Destination Port Range**: `80,443`
   - **Add Ingress Rules**.

## Parte 5 — Entrar na máquina (5 min)

No Windows, abra o **PowerShell** (tecla Windows, digite *PowerShell*).
Cole estas duas linhas (troque SEU_IP pelo número copiado):

```powershell
icacls "$env:USERPROFILE\Downloads\radar.key" /inheritance:r /grant:r "$($env:USERNAME):(R)"
ssh -i "$env:USERPROFILE\Downloads\radar.key" ubuntu@SEU_IP
```

Na primeira vez ele pergunta *"Are you sure you want to continue
connecting?"*: digite `yes` e Enter. Quando aparecer algo como
`ubuntu@radar:~$`, você está dentro da máquina.

(No Mac: Terminal, `chmod 400 ~/Downloads/radar.key` e
`ssh -i ~/Downloads/radar.key ubuntu@SEU_IP`.)

## Parte 6 — Instalar o Radar (5 min de você + 15 min esperando)

1. Deixe aberta outra aba com o **Render** → serviço **radar-api-navega**
   → **Environment**. Você vai copiar 4 valores de lá.
2. No PowerShell (dentro da máquina), cole:

```bash
curl -fsSL https://raw.githubusercontent.com/ddifreju/plataformaerp/main/infra/oracle/instalar.sh -o instalar.sh
sudo bash instalar.sh
```

3. O instalador pergunta, um de cada vez:
   `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
   `SPRING_DATASOURCE_PASSWORD` e `APP_DOCUMENTO_HMAC_CHAVE`.
   Copie cada valor do Render e cole com **botão direito** no PowerShell,
   depois Enter. As duas senhas **não aparecem** enquanto você cola: é
   normal. ⚠️ Não mande esses valores no chat.
4. Espere. No fim aparece em verde:
   **PRONTO! Abra: https://SEU-IP-com-tracos.sslip.io/radar**
5. Abra esse endereço, entre e mande o endereço para o Claude.

Pronto. A partir daí, cada mudança que o Claude publicar na `main` entra
sozinha no ar em poucos minutos (a máquina confere o GitHub a cada 2
minutos).

## Depois que estiver funcionando

- Deixe o Render como está por uns dias, por segurança. Depois, no
  Render, use **Suspend** nos dois serviços (não custa nada e não apaga).
- O endereço `sslip.io` é grátis e funciona com HTTPS. Quando houver um
  domínio próprio, troca-se uma linha no `.env` da máquina.

## Comandos úteis (dentro da máquina)

| Para quê | Comando |
| --- | --- |
| Ver se está tudo de pé | `sudo docker ps` |
| Ver os erros do servidor | `sudo docker logs radar-backend --tail 50` |
| Forçar atualização agora | `sudo /opt/radar/infra/oracle/atualizar.sh --forcar` |
| Ver o histórico de deploys | `journalctl -u radar-atualizar -n 50` |
| Reiniciar tudo | `cd /opt/radar/infra/oracle && sudo docker compose --env-file .env restart` |

## Por que a máquina não "some"

A Oracle recupera máquinas grátis paradas (CPU, rede **e** memória abaixo
de 20% por 7 dias). O servidor reserva 2 GB de memória de propósito
(`infra/oracle/docker-compose.yml`), o que mantém a máquina de 8 GB sempre
acima de 20% e fora dessa regra.
