# Flujo de trabajo

- **Un solo proyecto**: esta carpeta es el proyecto maestro (`applicationId com.aistudio.glucoseframe.tabcg`). No se crean proyectos nuevos ni se reconstruye desde cero.
- **Git**: rama `main`; un commit descriptivo por cambio; etiqueta (`vX.Y.Z`) en cada versión probada. Volver atrás: `git log --oneline` y `git checkout <etiqueta>` (o `git revert <commit>`).
- **Antes de un cambio importante**: etiqueta/commit de seguridad + copia en `backups/` (ignorada por Git).
- **Versiones**: `versionCode` sube en cada APK que se instala; `versionName` = `1.<n>.0`.
- **Secretos**: nunca en Git (`*.jks`, `*.keystore`, `key.properties`, `.env`, `google-services.json`, `local.properties` están en `.gitignore`). Las contraseñas de firma se pasan por variables de entorno (`STORE_PASSWORD`, `KEY_PASSWORD`) como ya hace `app/build.gradle.kts`.
- **Antes de dar algo por terminado**: `./gradlew assembleDebug testDebugUnitTest` (Android Studio: Build → Make Project y Run Tests).
- **Publicación**: solo con autorización expresa; AAB firmado con `./gradlew bundleRelease`.
