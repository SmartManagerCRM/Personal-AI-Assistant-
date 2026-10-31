# DEV-ONLY signing key — NOT SECRET, NOT FOR RELEASE

`DEV-ONLY-private.pk8.b64` is committed so debug builds and tests are reproducible. Anyone with this repository can forge
licenses for builds that embed `public.x509.b64`.

Before distributing a commercial build:
1. `license-generator keygen --out ~/my-private-keys` (store the private key offline, never in git).
2. Replace `app/src/main/assets/license_public_key.b64` with the new `public.x509.b64` and rebuild.
3. Delete this directory.
