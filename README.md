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

As URLs devem usar HTTPS. Endpoints relativos são resolvidos a partir da URL do manifest. `{query}` e `{id}` são substituídos pelo aplicativo. Respostas de catálogo/busca usam `{ "items": [...] }`; fontes usam `{ "sources": [...] }`, com formato `mp4` ou `hls`.

## Desenvolvimento

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

O APK de desenvolvimento é criado em `app/build/outputs/apk/debug/app-debug.apk`. Ele é assinado apenas com a chave de depuração local e não deve ser publicado.

## Baixar o APK pelo GitHub no Android

O workflow **HK IPTV Mobile Lab - Debug APK** executa testes, lint e compilação a cada push na branch `main`, ou manualmente pela aba **Actions**. O APK só é publicado como artifact quando todas as verificações passam.

1. Abra este repositório no GitHub pelo navegador do celular.
2. Toque em **Actions** e selecione **HK IPTV Mobile Lab - Debug APK**.
3. Abra a execução mais recente marcada em verde.
4. Na seção **Artifacts**, toque em **HK-IPTV-Mobile-Lab-Debug**.
5. Extraia o arquivo ZIP baixado e abra `app-debug.apk`.
6. Se o Android solicitar, autorize temporariamente a instalação de aplicativos desconhecidos para o navegador ou gerenciador de arquivos usado.

É necessário estar conectado ao GitHub para baixar artifacts. O artifact é mantido por 14 dias. Trata-se de uma compilação de desenvolvimento, assinada somente com a chave de debug gerada pelo GitHub Actions; nenhuma release pública ou chave de produção é usada.

## Limitações da Fase 1

- Protocolo JSON é intencionalmente pequeno e ainda não possui paginação.
- Player suporta MP4 e HLS; DRM e legendas externas ainda não são suportados.
- Headers declarados são preservados, mas não existe editor de headers/credenciais na interface.
- Sem cache de catálogo, imagens de pôster ou reprodução offline.
- Testes são JVM e sintéticos; testes instrumentados/em dispositivo ainda não foram adicionados.
