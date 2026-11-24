# Ofertamática Mobile — Android 0.1

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
