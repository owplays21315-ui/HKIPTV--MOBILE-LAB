# HK IPTV Mobile Lab

Aplicativo Android independente para testar provedores declarados por manifests JSON. Não usa backend, proxy, addons ou aplicativos externos. Nenhum provedor real ou credencial é incluído no projeto.

## Requisitos

- Android Studio ou JDK 17
- Android SDK 35
- Dispositivo/emulador com Android 6.0 (API 23) ou superior

## Manifest suportado na Fase 1

```json
{
  "id": "synthetic-provider",
  "name": "Synthetic Provider",
  "version": "1.0",
  "description": "Dados de teste",
  "resources": ["catalog", "search", "metadata", "sources"],
  "endpoints": {
    "catalog": "catalog.json",
    "search": "search?q={query}",
    "metadata": "metadata/{id}.json",
    "sources": "sources/{id}.json"
  },
  "headers": {}
}
```

As URLs devem usar HTTPS. Endpoints relativos são resolvidos a partir da URL do manifest. `{query}`, `{type}` e `{id}` são substituídos pelo aplicativo. Respostas de catálogo/busca usam `{ "items": [...] }`; fontes usam `{ "sources": [...] }`, com formato `mp4` ou `hls`.

### Formato nativo adicional

Também são reconhecidos manifests com `resources`, `types`, `catalogs` e `idPrefixes`, sem exigir o bloco `endpoints`. Uma URL direta `/manifest.json` pode ser usada; ao receber HTML ou HTTP 404 em uma URL raiz, o aplicativo tenta o caminho `/manifest.json` preservando segmentos e parâmetros de configuração.

Para esse formato, as rotas são derivadas localmente:

- `catalog/{type}/{id}.json`
- `catalog/{type}/{id}/{extras}.json`
- `meta/{type}/{id}.json`
- `stream/{type}/{id}.json`

Provedores somente de streams são aceitos e podem fornecer fontes para itens descobertos por outro provedor ativo. Fontes continuam exigindo seleção manual. O aplicativo não inclui SDKs de outros clientes, não altera autenticação e não registra URLs, headers ou tokens nos logs.

## Desenvolvimento

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

O APK de desenvolvimento é criado em `app/build/outputs/apk/debug/app-debug.apk`. Ele é assinado apenas com a chave de depuração local e não deve ser publicado.

## Reprodução HTTP experimental

O player aceita HTTPS normalmente. Para testar uma fonte HTTP direta ou um redirecionamento HTTPS → HTTP, abra **Ajustes** e ative **Reprodução HTTP experimental**. A opção fica desligada por padrão, é salva somente no dispositivo e não altera as requisições de manifests.

Como os hosts de mídia são dinâmicos, o Android exige permissão cleartext no processo inteiro. O Lab declara essa permissão no manifest, mas o cliente usado pelo player continua bloqueando HTTP enquanto a opção estiver desligada. Mesmo com a opção ativa, cada destino e cada resolução DNS são validados; loopback, redes privadas, link-local, multicast, metadados de nuvem, esquemas não HTTP(S), loops e excesso de redirecionamentos permanecem bloqueados.

`Authorization`, `Cookie`, `Proxy-Authorization` e headers que aparentem carregar tokens, sessões ou segredos não são encaminhados em mudança de host ou downgrade HTTPS → HTTP. Uma fonte que dependa dessas credenciais pode não reproduzir; o diagnóstico técnico informa a retenção sem exibir valores, URLs completas ou parâmetros.

## Baixar o APK pelo GitHub no Android

O workflow **HK IPTV Mobile Lab - Debug APK** executa testes, lint e compilação a cada push na branch `main`, ou manualmente pela aba **Actions**. O APK só é publicado como artifact quando todas as verificações passam.

1. Abra este repositório no GitHub pelo navegador do celular.
2. Toque em **Actions** e selecione **HK IPTV Mobile Lab - Debug APK**.
3. Abra a execução mais recente marcada em verde.
4. Na seção **Artifacts**, toque em **HK-IPTV-Mobile-Lab-Debug**.
5. Extraia o arquivo ZIP baixado e abra `app-debug.apk`.
6. Se o Android solicitar, autorize temporariamente a instalação de aplicativos desconhecidos para o navegador ou gerenciador de arquivos usado.

É necessário estar conectado ao GitHub para baixar artifacts. O artifact é mantido por 14 dias. Trata-se de uma compilação de desenvolvimento, assinada somente com a chave de debug gerada pelo GitHub Actions; nenhuma release pública ou chave de produção é usada.

## Limitações conhecidas

- Protocolo JSON é intencionalmente pequeno e ainda não possui paginação.
- Player suporta MP4 e HLS; DRM e legendas externas ainda não são suportados.
- Headers declarados são preservados, mas não existe editor de headers/credenciais na interface.
- Sem cache de catálogo, imagens de pôster ou reprodução offline.
- Testes são JVM e sintéticos; testes instrumentados/em dispositivo ainda não foram adicionados.
- HTTP não oferece confidencialidade nem garantia contra alteração do conteúdo em trânsito.
- A defesa contra DNS rebinding bloqueia mudanças de endereço durante uma tentativa; alguns serviços com DNS muito dinâmico podem exigir nova tentativa.
- A permissão cleartext do Android é global para o processo por limitação da plataforma, embora o uso esteja restrito pelo cliente do player e pelo ajuste opt-in.
