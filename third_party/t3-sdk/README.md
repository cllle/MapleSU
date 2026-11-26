# T3 Verify SDK

Retained official Java SDK for the manager's T3 account integration.

- Primary source: user-supplied `T3SDK_Java.zip`, entry `T3SDK_Java/t3sdk/T3Verify.java`, supplied 2026-11-14.
- ZIP SHA-256: `daff80b513a109c75b54097948b9b2f41a46c56fdb8cf8f471a8185898ae96bd`
- Original ZIP `T3Verify.java` SHA-256: `df25f88d662c4a986f129b55bcb2403d66295a081fe6df5bfabccf5ce120152f`
- Preserved package: `t3sdk`.
- Comparison coordinates: `com.t3yanzheng:t3-verify-sdk:1.0.1`
- Comparison source artifact: `https://repo.maven.apache.org/maven2/com/t3yanzheng/t3-verify-sdk/1.0.1/t3-verify-sdk-1.0.1-sources.jar`, retrieved 2026-11-14.
- Publisher: barichan / T3 验证; upstream POM declares MIT License.
- Source JAR SHA-256: `cae3d6adfe511496484e4e43f027e8bfdfaf3903d66fef40875278c958a6e098`
- Maven `T3Verify.java` SHA-256: `44c44136c6d895c59ec209df3e4c3d56b13e57053694a17327bae94dc326e1b2`
- Maven `pom.xml` SHA-256: `ad59530cdf90fbbf6c06b40382372d9a100bd9d6a9569dfe9777a3451b7c108a`

`upstream/T3Verify.java` retains the ZIP entry byte for byte. `upstream/maven/` retains the comparison source and POM. The ZIP differs from Maven 1.0.1 only in package name and the order of QQ-related fields, setters, and method declarations; see `upstream/maven-comparison.patch`.
Only `src/main/java` is compiled into the manager; examples are omitted.
`maplesu.patch` records the complete local source delta.

Local changes:

1. Add `initPlain(noticeCode)` for the configured application with encryption and signing disabled. Values remain normal UTF-8 form fields; the SDK omits `s` only in this mode.
2. Parse complete, strict JSON objects (including nested metadata), rejecting truncated input, invalid syntax, duplicate object keys, trailing content, and nesting beyond 64 levels. Accept numeric and string JSON status codes, including `"200"` for user login and announcements; numeric codes use exact integer conversion so fractional or overflowing codes never become `200`.
3. Preserve response line breaks and cap responses at 256 Ki Java characters, closing the input stream on success or failure.
4. Keep the SDK's six HTTPS failover routes and existing per-route timeouts (3 seconds connect, 7 seconds read); stop retries when the calling thread is interrupted.
5. Expose `httpPostOnce` and the bounded response reader as protected test seams. No reflection or DNS changes are used by tests.

The manager adapter calls the official `userRegister`, `userLogin`, `userHeartbeat`, `modifyCoreByUser`, and `getNotice` methods. The account client supplies credentials and owns session persistence; neither SDK integration nor adapter logs account state codes.
