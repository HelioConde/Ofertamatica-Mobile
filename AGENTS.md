# Development rules
- Preserve Expo Camera and native Gertec TCP integration.
- Never publish internal IPs, credentials, actual product dumps or APK signing secrets.
- Read-only lookup; no automatic polling or ERP writes.
- Run npm test before changes, verify Android build in Actions.
- Do not claim real server or camera validation from simulator tests.
- Keep Gertec framing assumptions documented and reject ambiguous responses.
