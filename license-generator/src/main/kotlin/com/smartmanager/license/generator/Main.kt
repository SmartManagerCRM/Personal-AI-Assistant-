package com.smartmanager.license.generator

import com.smartmanager.license.*
import java.io.File
import java.util.Base64
import kotlin.system.exitProcess

private const val USAGE = """SmartManager License Generator (offline — no server, no network)

  keygen --out <dir>                         create private.pk8.b64 / public.x509.b64 (keep private key SECRET and off the phone)
  issue  --key <private.pk8.b64> --device <DEVICE-CODE> --type lifetime|timed [--days N] [--customer NAME] [--id ID]
  verify --pub <public.x509.b64> --device <DEVICE-CODE> --license <TEXT|@file>

The DEVICE-CODE is displayed in the app under Settings → License."""

fun main(args: Array<String>) {
    if (args.isEmpty()) { println(USAGE); exitProcess(1) }
    val opts = args.drop(1).chunked(2).filter { it.size == 2 }.associate { it[0].removePrefix("--") to it[1] }
    when (args[0]) {
        "keygen" -> {
            val dir = File(opts["out"] ?: error("--out required")).apply { mkdirs() }
            val (priv, pub) = LicenseKeys.generate()
            File(dir, "private.pk8.b64").writeText(Base64.getEncoder().encodeToString(priv))
            File(dir, "public.x509.b64").writeText(Base64.getEncoder().encodeToString(pub))
            println("Keys written to $dir. Embed public.x509.b64 as app/src/main/assets/license_public_key.b64 and rebuild the app.")
        }
        "issue" -> {
            val priv = Base64.getDecoder().decode(File(opts["key"] ?: error("--key required")).readText().trim())
            val device = opts["device"] ?: error("--device required")
            val type = LicenseType.valueOf((opts["type"] ?: "lifetime").uppercase())
            val now = System.currentTimeMillis() / 1000
            val exp = if (type == LicenseType.TIMED) now + (opts["days"]?.toLong() ?: error("--days required for timed")) * 86400 else 0
            val id = opts["id"] ?: java.util.UUID.randomUUID().toString().take(8)
            println(LicenseSigner(priv).issue(LicensePayload(id, type, device, now, exp, opts["customer"].orEmpty())))
        }
        "verify" -> {
            val pub = Base64.getDecoder().decode(File(opts["pub"] ?: error("--pub required")).readText().trim())
            val lic = (opts["license"] ?: error("--license required")).let { if (it.startsWith("@")) File(it.drop(1)).readText() else it }
            println(LicenseVerifier(pub).verify(lic, opts["device"] ?: error("--device required"), System.currentTimeMillis() / 1000))
        }
        else -> { println(USAGE); exitProcess(1) }
    }
}
