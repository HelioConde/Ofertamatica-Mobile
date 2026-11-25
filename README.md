# Consultor de Preço Primor Supermercado — Android 0.1.2

Consulta de preços somente leitura com Expo Camera e TCP Gertec. Configure o IP autorizado pela TI e a porta 6500 no aplicativo. Não publique endereços internos ou credenciais nem exponha TCP à internet.

## Desenvolvimento
Node 22 LTS, JDK 17, Android SDK.
```sh
npm install
npm test
npx expo run:android
```
Expo Go não suporta o módulo TCP nativo. GitHub Actions produz APK de teste após validação. Valide primeiro com simulador, depois com a TI na rede autorizada.

## Protocolo
Espera `#ok`, identifica-se com `#tc406|6.5.1`, envia `#EAN`, aceita `#Nome|R$ 6,99`, `#Nome|SEM PRECO` e `#nfound`. Suporta keep-alive e encerra após consulta. Enquadramento real ainda não homologado; silêncio de recepção de 350 ms e Latin-1 são provisórios. Rejeita respostas desconhecidas. Não há cache de preços ou alterações no ERP.

## Testes
`npm test` utiliza TCP local simulado; não substitui a validação Android e Gertec real.

## 0.1.1 — Compatibilidade legada
Aceita também `# PRECO: R$ 6,99 <metadados>|PRODUTO FICTICIO` com controles entre o preço e a descrição. Tempo de negociação sem confirmação: 3 s; limite total: 12 s. Diagnósticos separam conexão, saudação, consulta sem resposta e resposta não reconhecida. Não registra endereços internos nem conteúdo bruto de respostas. Teste real no celular ainda necessário.

## 0.1.2 — Configuração do servidor
Menu no canto superior direito, oculto por padrão. Configurações validadas e salvas localmente no aparelho. Configure o secret de Actions `GERTEC_HOST` com o IPv4 autorizado (Settings > Secrets and variables > Actions). O build inclui esse endereço como padrão, portanto ele é recuperável do APK distribuído; distribua apenas a destinatários autorizados. Sem secret, o campo IP começa vazio e a porta é 6500. No desenvolvimento local, use a variável `EXPO_PUBLIC_GERTEC_HOST` em ambiente não versionado. Não publique credenciais nem IPs internos no código.
