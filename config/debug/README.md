# Debug signing key

`steakrush-debug.jks` is a public debug-only signing key for SteakRush test APKs.

It is committed intentionally so local debug builds and GitHub Actions debug
builds use the same certificate. This keeps `com.steakrush` installable over
previous debug builds without Android reporting a signature mismatch.

Do not use this key for production release APKs or AABs.
