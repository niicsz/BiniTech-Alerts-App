# BiniTech Alertas (Android)

App em Kotlin (Jetpack Compose) que toca **alarme** quando o BiniTech Alerts detecta lentidão (acima de 1 s) ou queda em produção.

## Como funciona
- Um serviço em primeiro plano mantém uma conexão **SSE** com `GET /api/alerts/stream`, com reconexão automática (2 s a 60 s) e watchdog: sem `ping` por 75 s, reconecta.
- Em um alerta `noisy`, toca o toque de alarme **em loop no canal de alarme**, com volume máximo, vibração e tela cheia, até alguém tocar em **Reconhecer**. O reconhecimento chama `POST /api/alerts/{id}/ack`; se outro aparelho reconhecer, o alarme para aqui também.
- Avisos de normalização chegam como notificação comum, sem alarme.
- Reinicia sozinho depois de reiniciar o celular.

## Configuração no app
1. Instale o `.apk` (permita "instalar apps desconhecidos").
2. Informe a URL `https://...` do serviço e o token (`ALERTS_API_TOKEN`).
3. Ative **Monitorar produção**, permita notificações, "rodar em segundo plano" e "alarme em tela cheia".
4. Toque em **Testar alarme**.

## Build
```
gradle assembleRelease
```
Assinatura pelas variáveis `ALERTS_KEYSTORE_FILE`, `ALERTS_KEYSTORE_PASSWORD`, `ALERTS_KEY_ALIAS` e `ALERTS_KEY_PASSWORD`. Sem elas, usa a chave de debug.
